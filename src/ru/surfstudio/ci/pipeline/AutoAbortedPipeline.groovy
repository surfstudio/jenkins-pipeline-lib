package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.AbortDublicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

abstract class AutoAbortedPipeline extends Pipeline {

    public static final String INIT = 'Init'

    public static final String NEED_CHECK_SAME_BUILDS_PARAM_NAME = 'needCheckSameBuilds'


    private needCheckSameBuilds = true
    public abortStrategy

    AutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def final init() {
        abortStrategy = AbortDublicateStrategy.SELF

        initInternal()

        applyParameterIfNotEmpty(script, NEED_CHECK_SAME_BUILDS_PARAM_NAME, script.params[NEED_CHECK_SAME_BUILDS_PARAM_NAME], {
            value -> this.needCheckSameBuilds = value
        })

        if (needCheckSameBuilds) {
            //modify pipeline for execution only for abort duplicate builds

            this.node = NodeProvider.getAutoAbortNode()
            this.preExecuteStageBody = {}
            this.postExecuteStageBody = {}

            //extent init stage for abort duplicate builds
            def initBody = getStage(INIT).body
            getStage(INIT).body = {
                initBody()
                def buildIdentifier = getBuildIdentifier()
                script.currentBuild.rawBuild.setDescription("$buildIdentifier")
                def needContinueBuild = true
                switch (abortStrategy){
                    case AbortDublicateStrategy.SELF:
                        if(CommonUtil.isSameBuildRunning(script)){
                            needContinueBuild = false
                        }
                        break;
                    case AbortDublicateStrategy.ANOTHER:
                        CommonUtil.tryAbortSameBuilds(script)
                        break;
                }
                script.currentBuild.rawBuild.setDescription("$buildIdentifier abort duplicate" )

                if (needContinueBuild) {
                    CommonUtil.startCurrentBuildCloneWithParams(script, [
                            script.booleanParam(name: NEED_CHECK_SAME_BUILDS_PARAM_NAME, value: false)
                    ])
                }
            }
            //skip all another stages
            for (stage in stages) {
                if(stage.name != INIT) {
                    stage.strategy = StageStrategy.SKIP_STAGE
                }
            }
            //remove build from history when it ending
            this.finalizeBody = {
                script.currentBuild.rawBuild.delete()
            }
        } else {
            //extent normal init stage for storing identifier as description
            def initBody = getStage(INIT).body
            getStage(INIT).body = {
                initBody()
                def buildIdentifier = getBuildIdentifier()
                script.currentBuild.rawBuild.setDescription("$buildIdentifier")
            }
        }

    }

    abstract def initInternal()

    abstract def String getBuildIdentifier()
}
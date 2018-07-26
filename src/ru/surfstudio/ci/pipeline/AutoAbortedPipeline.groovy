package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.AbortDublicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

abstract class AutoAbortedPipeline extends Pipeline {

    public static final String INIT = 'Init'

    public static final String NEED_CHECK_SAME_BUILDS_PARAM_NAME = 'needCheckSameBuilds' //todo refactor name


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
                def needContinueBuild = true
                switch (abortStrategy){
                    case AbortDublicateStrategy.SELF:
                        if(CommonUtil.isOlderBuildWithDescriptionRunning(script, buildIdentifier)){
                            needContinueBuild = false
                            echo "Build skipped"
                        }
                        break;
                    case AbortDublicateStrategy.ANOTHER:
                        CommonUtil.tryAbortOlderBuildsWithDescription(script, buildIdentifier)
                        break;
                }
                script.currentBuild.rawBuild.setDescription("check same for \"$buildIdentifier\"" )

                if (needContinueBuild) {
                    //start clone of this build, which make main pipeline work
                    CommonUtil.startCurrentBuildCloneWithParams(script, [
                            script.booleanParam(name: NEED_CHECK_SAME_BUILDS_PARAM_NAME, value: false)
                    ])
                }
            }
            //clear all another stages
            for (stage in stages) {
                if(stage.name != INIT) {
                    stage.strategy = StageStrategy.SKIP_STAGE
                    stage.body = {}
                }
            }
            //remove build from history when it ending
            this.finalizeBody = {
                script.currentBuild.rawBuild.delete()
            }
        } else {
            //extent normal init stage for storing identifier as description, it will be used for searching same builds
            def initBody = getStage(INIT).body
            getStage(INIT).body = {
                initBody()
                setIdentifierDescription()
            }
        }
    }

    abstract def initInternal()

    abstract def String getBuildIdentifier()

    def getNeedCheckSameBuilds() {
        return needCheckSameBuilds
    }

    private def setIdentifierDescription(){
        script.currentBuild.rawBuild.setDescription("${getBuildIdentifier()}")
    }
}
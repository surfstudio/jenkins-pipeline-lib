package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.AbortDublicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

abstract class AutoAbortedPipeline extends Pipeline {

    public static final String INIT = 'Init'

    public static final String NEED_CHECK_SAME_BUILDS_PARAM_NAME = 'needCheckSameBuilds'


    public needCheckSameBuilds = true
    public abortStrategy

    AutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def final init() {
        abortStrategy = AbortDublicateStrategy.SELF

        initInternal()

        def buildIdentifier = getBuildIdentifier()
        script.currentBuild.rawBuild.setDescription("$buildIdentifier abort duplicate" )

        applyParameterIfNotEmpty(script, NEED_CHECK_SAME_BUILDS_PARAM_NAME, script.params[NEED_CHECK_SAME_BUILDS_PARAM_NAME], {
            value -> this.needCheckSameBuilds = value
        })

        if(needCheckActiveDublicateBuilds) {
            this.node = NodeProvider.getAutoAbortNode()
            this.preExecuteStageBody = {}
            this.postExecuteStageBody = {}
            def initBody = getStage(INIT).body
            getStage(INIT).body = {
                initBody()
                if (needCheckActiveDublicateBuilds) {
                    CommonUtil.tryAbortDuplicateBuilds(script, "${getBuildIdentifier()}", abortStrategy)
                    CommonUtil.restartCurrentBuildWithParams(script, [
                            script.booleanParam(name: NEED_CHECK_SAME_BUILDS_PARAM_NAME, value: false)
                    ])
                }
            }
        }
    }


    abstract def initInternal()

    abstract def getBuildIdentifier()
}
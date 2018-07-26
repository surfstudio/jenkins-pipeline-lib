package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

/**
 * Pipeline, который может отменять одинаковые билды
 * При старте pipeline выполняет отмену билдов в соответствии с {@link AbortDuplicateStrategy} сразу после выполнения
 * Init stage, который является обязательным для этого pipeline.
 * После отмены при необходимости стартует оснавная логика пайплайна как копия запущенного пайплайна, причем запущенный пайплайн удаляется
 */
abstract class AutoAbortedPipeline extends Pipeline {

    public static final String INIT = 'Init'

    public static final String ABORT_DUPLICATE_PIPELINE_MODE_PARAM_NAME = 'abortDuplicatePipelineMode'


    public abortDuplicatePipelineMode = true
    public abortStrategy  //see AbortDuplicateStrategy

    AutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def final init() {
        abortStrategy = AbortDuplicateStrategy.SELF

        initInternal()

        applyParameterIfNotEmpty(script,
                ABORT_DUPLICATE_PIPELINE_MODE_PARAM_NAME,
                script.params[ABORT_DUPLICATE_PIPELINE_MODE_PARAM_NAME], {
            value -> this.abortDuplicatePipelineMode = value
        })

        if (abortDuplicatePipelineMode) {
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
                    case AbortDuplicateStrategy.SELF:
                        if(CommonUtil.isOlderBuildWithDescriptionRunning(script, buildIdentifier)){
                            needContinueBuild = false
                            echo "Build skipped"
                        }
                        break;
                    case AbortDuplicateStrategy.ANOTHER:
                        CommonUtil.tryAbortOlderBuildsWithDescription(script, buildIdentifier)
                        break;
                }
                script.currentBuild.rawBuild.setDescription("check same for \"$buildIdentifier\"" )

                if (needContinueBuild) {
                    //start clone of this build, which make main pipeline work
                    CommonUtil.startCurrentBuildCloneWithParams(script, [
                            script.booleanParam(name: ABORT_DUPLICATE_PIPELINE_MODE_PARAM_NAME, value: false)
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
                setIdentifierAsDescription()
            }
        }
    }

    abstract def initInternal()

    abstract def String getBuildIdentifier()

    def isAbortDuplicatePipelineMode() {
        return abortDuplicatePipelineMode
    }

    private def setIdentifierAsDescription(){
        script.currentBuild.rawBuild.setDescription("${getBuildIdentifier()}")
    }
}
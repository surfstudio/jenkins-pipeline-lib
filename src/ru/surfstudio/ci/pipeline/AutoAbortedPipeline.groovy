package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

/**
 * Pipeline, который может отменять одинаковые билды
 * При старте pipeline выполняет отмену таких же билдов в соответствии с {@link AbortDuplicateStrategy} сразу после выполнения
 * Init stage
 * После отмены при необходимости стартует оснавная логика пайплайна как копия запущенного пайплайна, причем запущенный пайплайн удаляется
 *
 * Для этого пайплайна необходимо переопределить метод initInternal вместо init и метод getBuildIdentifier
 * Значение, которое вернет getBuildIdentifier, будет применено как описание pipeline и имеено по описанию будут определяться идентичные билды
 */
abstract class AutoAbortedPipeline extends Pipeline {

    public static final String ABORT_DUPLICATE_PIPELINE_MODE_PARAM_NAME = 'abortDuplicatePipelineMode'

    public abortDuplicatePipelineMode = true
    public abortStrategy  //see AbortDuplicateStrategy
    public deletingBuildsWithAbortDuplicatePipelineModeEnabled = true

    AutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def final init() {
        abortStrategy = AbortDuplicateStrategy.ANOTHER

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
            def originalInitStageBody = this.initStageBody
            this.initStageBody = {
                //execute original init stage body
                originalInitStageBody()
                //and add abort logic:
                def buildIdentifier = getBuildIdentifier()
                def needContinueBuild = true
                switch (abortStrategy){
                    case AbortDuplicateStrategy.SELF:
                        if(CommonUtil.isOlderBuildWithDescriptionRunning(script, buildIdentifier)){
                            needContinueBuild = false
                            script.echo "Build skipped"
                        }
                        break;
                    case AbortDuplicateStrategy.ANOTHER:
                        CommonUtil.tryAbortOlderBuildsWithDescription(script, buildIdentifier)
                        break;
                }
                script.currentBuild.rawBuild.setDescription("check active duplicates for \"$buildIdentifier\"" )

                if (needContinueBuild) {
                    //start clone of this build, which make main pipeline work
                    CommonUtil.startCurrentBuildCloneWithParams(script, [
                            script.booleanParam(name: ABORT_DUPLICATE_PIPELINE_MODE_PARAM_NAME, value: false)
                    ])
                }
            }
            //clear all another stages
            stages = []
            //remove build from history when it ending
            this.finalizeBody = {
                if(deletingBuildsWithAbortDuplicatePipelineModeEnabled) {
                    script.echo "Deleting build"
                    script.currentBuild.rawBuild.delete()
                }
            }
        } else {
            //extent normal init stage for storing identifier as description, it will be used for searching same builds
            def originalInitStageBody = this.initStageBody
            this.initStageBody = {
                originalInitStageBody()
                setIdentifierAsDescription()
            }
        }
    }

    abstract def initInternal()

    /**
     * @return уникальное имя для билда, по этому имени будут искаться похожие билды для отмены
     */
    abstract def String getBuildIdentifier()

    def isAbortDuplicatePipelineMode() {
        return abortDuplicatePipelineMode
    }

    def setIdentifierAsDescription(){
        script.currentBuild.rawBuild.setDescription("${getBuildIdentifier()}")
    }
}
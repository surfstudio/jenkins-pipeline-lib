package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.pipeline.pr.PrPipeline

/**
 * @Deprecated see {@link PrPipeline}
 */
@Deprecated
class PrStages {

    @Deprecated
    def static initStageBody(PrPipeline ctx) {
       PrPipeline.initStageBody1(ctx)
    }

    @Deprecated
    def static preMergeStageBody(Object script, String url, String sourceBranch, String destinationBranch, String credentialsId) {
        PrPipeline.preMergeStageBody(script, url, sourceBranch, destinationBranch, credentialsId)
    }

    @Deprecated
    def static prepareMessageForPipeline(PrPipeline ctx, Closure handler) {
        PrPipeline.prepareMessageForPipeline(ctx, handler)
    }

    @Deprecated
    def static finalizeStageBody(PrPipeline ctx){
        PrPipeline.finalizeStageBody(ctx)
    }

    @Deprecated
    def static debugFinalizeStageBody(PrPipeline ctx) {
        PrPipeline.debugFinalizeStageBody(ctx)
    }
}
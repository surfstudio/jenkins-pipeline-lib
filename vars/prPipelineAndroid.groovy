import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.PrPipelineAndroid
import static ru.surfstudio.ci.CommonUtil.*

def call(PrPipelineAndroid ctx) {
    ctx.origin.node(NodeProvider.getAndroidNode()) {
        try {
            ctx.origin.stage('Init') {
                prInitStage.call(ctx)
            }
            stageWithStrategy(ctx, 'PreMerge', ctx.preMergeStageStrategy) {
                prPreMergeStage.call(ctx)
            }
            stageWithStrategy(ctx, 'Build', ctx.buildStageStrategy) {
                buildStageAndroid.call(ctx, ctx.buildGradleTask)
            }
            stageWithStrategy(ctx, 'Unit Test', ctx.unitTestStageStrategy) {
                unitTestStageAndroid.call(ctx,
                        ctx.unitTestGradleTask,
                        ctx.unitTestResultPathXml,
                        ctx.unitTestResultPathDirHtml)
            }
            stageWithStrategy(ctx, 'Small Instrumentation Test', ctx.smallInstrumentationTestStageStrategy) {
                //smallInstrumentationTestStageBody()
            }
            stageWithStrategy(ctx, 'Static Code Analysis', ctx.staticCodeAnalysisStageStrategy) {
                //staticCodeAnalysisStageBody()
            }
        } finally {
            //finalizeBody()
        }
    }
}

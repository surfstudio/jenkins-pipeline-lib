import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.PrContext
import static ru.surfstudio.ci.CommonUtil.*
import jenkins.model.CauseOfInterruption.UserInterruption

def call(PrContext ctx) {
    ctx.origin.node(NodeProvider.getAndroidNode()) {
        try {
            ctx.origin.stage('Init') {
                new UserInterruption(
                        "Aborted by newer build #${ctx.origin.currentBuild.number}"
                )
                prInitStage.call(ctx)
            }
            stageWithStrategy(ctx, 'PreMerge', ctx.preMergeStageStrategy) {
                //preMergeStageBody()
            }
            stageWithStrategy(ctx, 'Build', ctx.buildStageStrategy) {
                //buildStageBody()
            }
            stageWithStrategy(ctx, 'Unit Test', ctx.unitTestStageStrategy) {
                //unitTestStageBody()
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

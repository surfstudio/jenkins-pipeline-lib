package ru.surfstudio.ci

class AndroidPrPipelineExecutor extends BasePiplineExecutor<PrContext> {

    AndroidPrPipelineExecutor(PrContext ctx) {
        super(ctx)
    }

    @Override
    void run() {
        node("android.node") {
            try {
                stage('Init') {
                    initStageBody()
                }
                stageWithStrategy('PreMerge', preMergeStageStrategy) {
                    preMergeStageBody()
                }
                stageWithStrategy('Build', buildStageStrategy) {
                    buildStageBody()
                }
                stageWithStrategy('Unit Test', unitTestStageStrategy) {
                    unitTestStageBody()
                }
                stageWithStrategy('Small Instrumentation Test', smallInstrumentationTestStageStrategy) {
                    smallInstrumentationTestStageBody()
                }
                stageWithStrategy('Static Code Analysis', staticCodeAnalysisStageStrategy) {
                    staticCodeAnalysisStageBody()
                }
            } finally {
                finalizeBody()
            }
        }
    }
}

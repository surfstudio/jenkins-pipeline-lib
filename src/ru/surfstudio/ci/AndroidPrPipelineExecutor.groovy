package ru.surfstudio.ci

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty
import static ru.surfstudio.ci.CommonUtil.printDefaultVar

class AndroidPrPipelineExecutor extends BasePipelineExecutor<PrContext> {

    AndroidPrPipelineExecutor(PrContext ctx) {
        super(ctx)
    }

    @Override
    public void run() {
        ctx.origin.node(NodeProvider.getAndroidNode()) {
            try {
                ctx.origin.stage('Init') {
                    initStageBody()
                }
                stageWithStrategy('PreMerge', ctx.preMergeStageStrategy) {
                    //preMergeStageBody()
                }
                stageWithStrategy('Build', ctx.buildStageStrategy) {
                    //buildStageBody()
                }
                stageWithStrategy('Unit Test', ctx.unitTestStageStrategy) {
                    //unitTestStageBody()
                }
                stageWithStrategy('Small Instrumentation Test', ctx.smallInstrumentationTestStageStrategy) {
                    //smallInstrumentationTestStageBody()
                }
                stageWithStrategy('Static Code Analysis', ctx.staticCodeAnalysisStageStrategy) {
                    //staticCodeAnalysisStageBody()
                }
            } finally {
                //finalizeBody()
            }
        }
    }

    public void initStageBody() {
        printDefaultVar(ctx, 'preMergeStageStrategy', ctx.preMergeStageStrategy)
        printDefaultVar(ctx, 'buildStageStrategy', ctx.buildStageStrategy)
        printDefaultVar(ctx, 'unitTestStageStrategy', ctx.unitTestStageStrategy)
        printDefaultVar(ctx, 'smallInstrumentationTestStageStrategy', ctx.smallInstrumentationTestStageStrategy)
        printDefaultVar(ctx, 'staticCodeAnalysisStageStrategy', ctx.staticCodeAnalysisStageStrategy)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        //Параметры могут быть установлены только если Job стартовали вручную
        applyParameterIfNotEmpty(ctx, 'preMergeStageStrategy', ctx.origin.params.preMergeStageStrategy) {
            param -> ctx.preMergeStageStrategy = param
        }
        applyParameterIfNotEmpty(ctx, 'buildStageStrategy', ctx.origin.params.buildStageStrategy) {
            param -> ctx.buildStageStrategy = param
        }
        applyParameterIfNotEmpty(ctx, 'unitTestStageStrategy', ctx.origin.params.unitTestStageStrategy) {
            param -> ctx.unitTestStageStrategy = param
        }
        applyParameterIfNotEmpty(ctx, 'smallInstrumentationTestStageStrategy', ctx.origin.params.smallInstrumentationTestStageStrategy) {
            param -> ctx.smallInstrumentationTestStageStrategy = param
        }
        applyParameterIfNotEmpty(ctx, 'staticCodeAnalysisStageStrategy', ctx.origin.params.staticCodeAnalysisStageStrategy) {
            param -> ctx.staticCodeAnalysisStageStrategy = param
        }

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        applyParameterIfNotEmpty(ctx, 'sourceBranch', ctx.origin.params.sourceBranch, {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(ctx, 'destinationBranch', ctx.origin.params.destinationBranch, {
            value -> ctx.destinationBranch = value
        })
        applyParameterIfNotEmpty(ctx, 'authorUsername',ctx.origin. params.authorUsername, {
            value -> ctx.authorUsername = value
        })

        CommonUtil.abortPreviousBuilds(ctx, ctx.sourceBranch)
    }
}

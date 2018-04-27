package ru.surfstudio.ci

import static ru.surfstudio.ci.CommonUtil.stageWithStrategy

class PrPipelineAndroid extends PrPipeline {

    public buildGradleTask = "assembleQa"

    public unitTestGradleTask = "testQaUnitTest"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"


    PrPipelineAndroid(Object origin) {
        super(origin)
        node = NodeProvider.getAndroidNode()
        stages = [
                new Stage('Init', "", prInitStage prInitStage(ctx)) ????
        ]
    }


    try {
        ctx.origin.stage('Init') {

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
}
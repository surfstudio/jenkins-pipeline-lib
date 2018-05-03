package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.stage.body.PrStages

class PrPipelineAndroid extends PrPipeline {

    public buildGradleTask = "clean assembleQa"

    public unitTestGradleTask = "testQaUnitTest"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"

    public instrumentedTestGradleTask = "connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=small"
    public instrumentedTestResultPathXml = "**/outputs/androidTest-results/connected/*.xml"
    public instrumentedTestResultPathDirHtml = "app/build/reports/androidTests/connected/"


    PrPipelineAndroid(Object script) {
        super(script)
        node = NodeProvider.getAndroidNode()
        stages = [
                createStage('Init', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.initStageBody(this)
                },
                createStage('PreMerge', preMergeStageStrategy) {
                    PrStages.preMergeStageBody(origin, sourceBranch, destinationBranch)
                },
                createStage('Build', buildStageStrategy) {
                    CommonAndroidStages.buildStageBodyAndroid(origin, buildGradleTask)
                },
                createStage('Unit Test', unitTestStageStrategy) {
                    CommonAndroidStages.unitTestStageBodyAndroid(origin,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage('Small Instrumentation Test', smallInstrumentationTestStageStrategy) {
                    CommonAndroidStages.instrumentationTestStageBodyAndroid(origin,
                            instrumentedTestGradleTask,
                            instrumentedTestResultPathXml,
                            instrumentedTestResultPathDirHtml)
                },
                createStage('Static Code Analysis', staticCodeAnalysisStageStrategy) {
                    CommonAndroidStages.staticCodeAnalysisStageBody(origin)
                },

        ]
        finalizeBody = { PrStages.finalizeStageBody(this) }
    }
}
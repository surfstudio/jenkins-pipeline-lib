package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.stage.body.TagStages

class TagPipelineAndroid extends TagPipeline {

    public buildGradleTask = "clean assembleQa assembleRelease"
    public betaUploadGradleTask = "crashlyticsUploadDistributionQa"

    public unitTestGradleTask = "testQaUnitTest"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"

    public instrumentedTestGradleTask = "connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=small"
    public instrumentedTestResultPathXml = "**/outputs/androidTest-results/connected/*.xml"
    public instrumentedTestResultPathDirHtml = "app/build/reports/androidTests/connected/"


    TagPipelineAndroid(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getAndroidNode()
        stages = [
                createStage('Init', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagStages.initStageBody(this)
                },
                createStage('Checkout', checkoutStageStrategy) {
                    TagStages.checkoutStageBody(script, repoTag)
                },
                createStage('Build', buildStageStrategy) {
                    CommonAndroidStages.buildStageBodyAndroid(script, buildGradleTask)
                },
                createStage('Unit Test', unitTestStageStrategy) {
                    CommonAndroidStages.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage('Small Instrumentation Test', smallInstrumentationTestStageStrategy) {
                    CommonAndroidStages.instrumentationTestStageBodyAndroid(script,
                            instrumentedTestGradleTask,
                            instrumentedTestResultPathXml,
                            instrumentedTestResultPathDirHtml)
                },
                createStage('Static Code Analysis', staticCodeAnalysisStageStrategy) {
                    CommonAndroidStages.staticCodeAnalysisStageBody(script)
                },
                createStage('Beta Upload', betaUploadStageStrategy) {
                    TagStages.betaUploadStageBodyAndroid(script, betaUploadGradleTask)
                },

        ]
        finalizeBody = { TagStages.finalizeStageBody(this) }
    }
}
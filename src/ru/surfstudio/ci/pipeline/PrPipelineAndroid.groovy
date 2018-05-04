package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.stage.body.PrStages

import static ru.surfstudio.ci.CommonUtil.printDefaultVar

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
    }

    @Override
    def init() {
        node = NodeProvider.getAndroidNode()
        stages = [
                createStage('Init', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.initStageBody(this)
                },
                createStage('PreMerge', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.preMergeStageBody(script, sourceBranch, destinationBranch)
                },
                createStage('Build', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.buildStageBodyAndroid(script, buildGradleTask)
                },
                createStage('Unit Test', StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage('Small Instrumentation Test', StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.instrumentationTestStageBodyAndroid(script,
                            instrumentedTestGradleTask,
                            instrumentedTestResultPathXml,
                            instrumentedTestResultPathDirHtml)
                },
                createStage('Static Code Analysis', StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.staticCodeAnalysisStageBody(script)
                },

        ]
        finalizeBody = { PrStages.finalizeStageBody(this) }
    }
}
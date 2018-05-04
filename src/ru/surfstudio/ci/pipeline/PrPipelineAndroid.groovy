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
                createStage('PreMerge', preMergeStageStrategy) {
                    printDefaultVar(script, 'buildStageStrategy', buildStageStrategy)
                    PrStages.preMergeStageBody(script, sourceBranch, destinationBranch)
                },
                createStage('Build', buildStageStrategy) {
                    printDefaultVar(script, 'buildStageStrategy', buildStageStrategy)
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

        ]
        finalizeBody = { PrStages.finalizeStageBody(this) }
    }
}
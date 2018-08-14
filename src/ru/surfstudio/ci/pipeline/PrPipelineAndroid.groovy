package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
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
    }

    @Override
    def initInternal() {
        node = NodeProvider.getAndroidNode()

        preExecuteStageBody = { stage ->
            if(stage.name != PRE_MERGE) CommonUtil.notifyBitbucketAboutStageStart(script, stage.name)
        }
        postExecuteStageBody = { stage ->
            if(stage.name != PRE_MERGE) CommonUtil.notifyBitbucketAboutStageFinish(script, stage.name, stage.result)
        }

        initStageBody = {  PrStages.initStageBody(this) }
        stages = [
                createStage(PRE_MERGE, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.preMergeStageBody(script, sourceBranch, destinationBranch)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.buildStageBodyAndroid(script, buildGradleTask)
                },
                createStage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.instrumentationTestStageBodyAndroid(script,
                            instrumentedTestGradleTask,
                            instrumentedTestResultPathXml,
                            instrumentedTestResultPathDirHtml)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.staticCodeAnalysisStageBody(script)
                },

        ]
        finalizeBody = { PrStages.finalizeStageBody(this) }
    }
}

//def buildData = script.currentBuild.raw.getAction(hudson.plugins.git.util.BuildData.class);
//echo "last Build: ${buildData.getLastBuiltRevision()}"
//echo "last Build SHA1: ${buildData.getLastBuiltRevision().getSha1String()}"
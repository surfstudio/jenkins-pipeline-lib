package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
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

    public keystoreCredentials = "no_credentials"
    public keystorePropertiesCredentials = "no_credentials"


    TagPipelineAndroid(Object script) {
        super(script)
    }

    @Override
    def initInternal() {
        node = NodeProvider.getAndroidNode()

        preExecuteStageBody = { stage -> if(stage.name != CHECKOUT) CommonUtil.getBitbucketNotifyPreExecuteStageBody(script) }
        postExecuteStageBody = { stage -> if(stage.name != CHECKOUT) CommonUtil.getBitbucketNotifyPostExecuteStageBody(script) }

        initStageBody = {  TagStages.initStageBody(this) }
        stages = [
                createStage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagStages.checkoutStageBody(script, repoTag)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.buildWithCredentialsStageBodyAndroid(script,
                            buildGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },
                createStage(UNIT_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.instrumentationTestStageBodyAndroid(script,
                            instrumentedTestGradleTask,
                            instrumentedTestResultPathXml,
                            instrumentedTestResultPathDirHtml)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommonAndroidStages.staticCodeAnalysisStageBody(script)
                },
                createStage(BETA_UPLOAD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagStages.betaUploadWithKeystoreStageBodyAndroid(script,
                            betaUploadGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },

        ]
        finalizeBody = { TagStages.finalizeStageBody(this) }
    }
}
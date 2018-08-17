package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.AndroidUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy

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
    def init() {
        propertiesProvider = { TagPipeline.properties(this) }
        node = NodeProvider.getAndroidNode()

        preExecuteStageBody = TagPipeline.getPreExecuteStageBody(script, repoUrl)
        postExecuteStageBody = TagPipeline.getPostExecuteStageBody(script, repoUrl)

        initializeBody = { TagPipeline.initBody(this) }
        stages = [
                createStage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagPipeline.checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },
                createStage(UNIT_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.instrumentationTestStageBodyAndroid(script,
                            instrumentedTestGradleTask,
                            instrumentedTestResultPathXml,
                            instrumentedTestResultPathDirHtml)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.staticCodeAnalysisStageBody(script)
                },
                createStage(BETA_UPLOAD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagPipeline.betaUploadWithKeystoreStageBodyAndroid(script,
                            betaUploadGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },

        ]
        finalizeBody = { TagPipeline.finalizeStageBody(this) }
    }


    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ ======================================================

    def static betaUploadWithKeystoreStageBodyAndroid(Object script,
                                                      String betaUploadGradleTask,
                                                      String keystoreCredentials,
                                                      String keystorePropertiesCredentials) {
        AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials) {
            betaUploadStageBodyAndroid(script, betaUploadGradleTask)
        }
    }

    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        script.sh "./gradlew ${betaUploadGradleTask}"
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}
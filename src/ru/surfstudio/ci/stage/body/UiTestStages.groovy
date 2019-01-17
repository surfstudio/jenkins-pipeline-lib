package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.pipeline.ui_test.UiTestPipeline
import ru.surfstudio.ci.pipeline.ui_test.UiTestPipelineAndroid
import ru.surfstudio.ci.pipeline.ui_test.UiTestPipelineiOS

/**
 * @Deprecated see {@link UiTestPipeline}
 */
@Deprecated
class UiTestStages {

    @Deprecated
    def static initStageBody(UiTestPipeline ctx) {
        UiTestPipeline.initBody(ctx)
    }

    @Deprecated
    def static checkoutSourcesBody(Object script, String sourcesDir, String sourceRepoUrl, String sourceBranch) {
        def credentialsId = script.scm.userRemoteConfigs.first().credentialsId
        UiTestPipeline.checkoutSourcesBody(script, sourcesDir, sourceRepoUrl, sourceBranch, credentialsId)
    }

    @Deprecated
    def static checkoutTestsStageBody(Object script, String testBranch) {
        def credentialsId = script.scm.userRemoteConfigs.first().credentialsId
        def testsRpoUrl = script.scm.userRemoteConfigs.first().url
        UiTestPipeline.checkoutTestsStageBody(script, testsRpoUrl, testBranch, credentialsId)
    }

    @Deprecated
    def static buildStageBodyAndroid(Object script, String sourcesDir, String buildGradleTask) {
        UiTestPipelineAndroid.buildStageBodyAndroid(script, sourcesDir, buildGradleTask)
    }

    @Deprecated
    def static buildStageBodyiOS(Object script, String sourcesDir, String derivedDataPath, String sdk, String keychainCredenialId, String certfileCredentialId) {            
        UiTestPipelineiOS.buildStageBodyiOS(script, sourcesDir, derivedDataPath, sdk, keychainCredenialId, certfileCredentialId)
    }

    @Deprecated
    def static prepareApkStageBodyAndroid(Object script, String builtApkPattern, String newApkForTest) {
        UiTestPipelineAndroid.prepareApkStageBodyAndroid(script, builtApkPattern, newApkForTest)
    }

    /**
     * скачивает .feature для taskKey из Xray Jira и сохраняет в файл newFeatureForTest в папку featuresDir
     */
    @Deprecated
    def static prepareTestsStageBody(Object script,
                                     String jiraAuthenticationName,
                                     String taskKey,
                                     String featuresDir,
                                     String newFeatureForTest) {
        UiTestPipeline.prepareTestsStageBody(script, jiraAuthenticationName, taskKey, featuresDir, newFeatureForTest)
    }

    @Deprecated
    def static testStageBody(Object script,
                                    String taskKey,
                                    String outputsDir,
                                    String featuresDir,
                                    String platform,
                                    String artifactForTest,
                                    String featureFile,
                                    String outputHtmlFile,
                                    String outputJsonFile,
                                    String outputrerunTxtFile) {
        UiTestPipelineAndroid.testStageBodyAndroid(
                script,
                taskKey,
                outputsDir,
                featuresDir,
                artifactForTest,
                featureFile,
                outputHtmlFile,
                outputrerunTxtFile)

    }

    @Deprecated
    def static testStageBodyiOS(Object script,
                             String taskKey,
                             String sourcesDir,
                             String derivedDataPath,
                             String device,
                             String iosVersion,
                             String outputsDir,
                             String featuresDir,
                             String featureFile,
                             String outputHtmlFile,
                             String outputJsonFile) {

        UiTestPipelineiOS.testStageBodyiOS(script,
                taskKey,
                sourcesDir,
                derivedDataPath,
                device,
                iosVersion,
                outputsDir,
                featuresDir,
                featureFile,
                outputHtmlFile,
                outputJsonFile)
    }

    @Deprecated
    def static publishResultsStageBody(Object script,
                                       String outputsDir,
                                       String outputJsonFile,
                                       String outputHtmlFile,
                                       String outputrerunTxtFile
                                       String jiraAuthenticationName,
                                       String htmlReportName) {
        UiTestPipeline.publishResultsStageBody(script,
                outputsDir,
                outputJsonFile,
                outputHtmlFile,
                outputrerunTxtFile,
                jiraAuthenticationName,
                htmlReportName)
    }

    @Deprecated
    def static finalizeStageBody(UiTestPipeline ctx) {
        UiTestPipeline.finalizeStageBody(ctx)
    }

    // ================================== UTILS ===================================

    @Deprecated
    def static sendStartNotification(UiTestPipeline ctx) {
        UiTestPipeline.sendStartNotification(ctx)
    }

    @Deprecated
    def static sendFinishNotification(UiTestPipeline ctx) {
        UiTestPipeline.sendFinishNotification(ctx)
    }

    @Deprecated
    def static sendMessage(UiTestPipeline ctx, String message, boolean success) {
        UiTestPipeline.sendMessage(ctx, message, success)
    }

    @Deprecated
    def static checkAndParallelBulkJob(UiTestPipeline ctx) {
        UiTestPipeline.checkAndParallelBulkJob(ctx)
    }

    @Deprecated
    def static ifBulkJob(UiTestPipeline ctx){
        UiTestPipeline.isBulkJob(ctx)
    }

}
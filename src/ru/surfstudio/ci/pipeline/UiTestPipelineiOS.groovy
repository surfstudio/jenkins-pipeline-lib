package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommoniOSStages
import ru.surfstudio.ci.stage.body.TagStages
import ru.surfstudio.ci.stage.body.UiTestStages

class UiTestPipelineiOS extends UiTestPipeline {

    public artifactForTest = "for_test.apk"
    public buildGradleTask = "clean assembleQa"
    public builtApkPattern = "${sourcesDir}/**/*qa*.apk"

    //dirs
    public derivedDataPath = "${sourcesDir}"

    //files
    public simulatorIdentificationFile = "currentSim"

    //environment
    public testDeviceName = "iPhone 7"
    public testOSVersion = "11.4"
    public testiOSSDK = "iphonesimulator11.4"

    UiTestPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def initInternal() {
        node = NodeProvider.getiOSNode()
        stages = [
                createStage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.initStageBody(this)
                },
                createStage(CHECKOUT_SOURCES, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.checkoutSourcesBody(script, sourcesDir, sourceRepoUrl, sourceBranch,)
                },
                createStage(CHECKOUT_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.checkoutTestsStageBody(script, testBranch)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.buildStageBodyiOS(script, 
                            sourcesDir, 
                            derivedDataPath,
                            testiOSSDK,
                            iOSKeychainCredenialId, 
                            iOSCertfileCredentialId)
                },
                createStage(PREPARE_ARTIFACT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.prepareApkStageBodyAndroid(script,
                            builtApkPattern,
                            artifactForTest)
                    // TODO: Удалить
                },
                createStage(PREPARE_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.prepareTestsStageBody(script,
                            jiraAuthenticationName,
                            taskKey,
                            featuresDir,
                            featureForTest)
                },
                createStage(TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    UiTestStages.testStageBodyiOS(script,
                            taskKey,
                            sourcesDir,
                            derivedDataPath,
                            testDeviceName,
                            testOSVersion,
                            outputsDir,
                            featuresDir,
                            featureForTest,
                            outputHtmlFile,
                            outputJsonFile)
                },
                createStage(PUBLISH_RESULTS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    UiTestStages.publishResultsStageBody(script,
                            outputsDir,
                            outputJsonFile,
                            outputHtmlFile,
                            jiraAuthenticationName,
                            "UI Tests ${taskKey} ${taskName}")

                }

        ]
        finalizeBody = { UiTestStages.finalizeStageBody(this) }
    }
}

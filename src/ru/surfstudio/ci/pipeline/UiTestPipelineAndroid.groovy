package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.stage.body.TagStages
import ru.surfstudio.ci.stage.body.UiTestStages

class UiTestPipelineAndroid extends UiTestPipeline {

    public artifactForTest = "for_test.apk"
    public buildGradleTask = "clean assembleQa"
    public builtApkPattern = "${sourcesDir}/**/*qa*.apk"

    UiTestPipelineAndroid(Object script) {
        super(script)
    }

    @Override
    def init() {

        //default stage strategies
        node = NodeProvider.getAndroidNode()
        stages = [
                createStage('Init', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.initStageBody(this)
                },
                createStage('Checkout Sources', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.checkoutSourcesBody(script, sourcesDir, sourceRepoUrl, sourceBranch)
                },
                createStage('Checkout Tests', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.checkoutTestsStageBody(script, testBranch)
                },
                createStage('Build', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.buildStageBodyAndroid(script, sourcesDir, buildGradleTask)
                },
                createStage('Prepare Artifact', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.prepareApkStageBodyAndroid(script,
                            builtApkPattern,
                            artifactForTest)
                },
                createStage('Prepare Tests', StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    UiTestStages.prepareTestsStageBody(script,
                            jiraAuthenticationName,
                            taskKey,
                            featuresDir,
                            featureForTest)
                },
                createStage('Test', StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    UiTestStages.testStageBody(script,
                            taskKey,
                            outputsDir,
                            featuresDir,
                            "android",
                            artifactForTest,
                            featureForTest,
                            outputHtmlFile,
                            outputJsonFile)
                },
                createStage('Publish Results', StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
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

/*
  Copyright (c) 2018-present, SurfStudio LLC.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.android.AndroidTestConfig

class PrPipelineAndroid extends PrPipeline {

    //required initial configuration
    public keystoreCredentials = "no_credentials"
    public keystorePropertiesCredentials = "no_credentials"

    public buildGradleTask = "clean assembleQa"

    public unitTestGradleTask = "testQaUnitTest"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"

    public instrumentalTestGradleTask = "assembleAndroidTest"
    public instrumentalTestResultPathDirXml = "build/outputs/androidTest-results/instrumental"
    public instrumentalTestResultPathXml = "$instrumentalTestResultPathDirXml/*.xml"
    public instrumentalTestResultPathDirHtml = "build/reports/androidTests/instrumental"

    public AndroidTestConfig androidTestConfig = new AndroidTestConfig()

    PrPipelineAndroid(Object script) {
        super(script)
    }

    def init() {
        node = NodeProvider.getAndroidNode()

        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                createStage(PRE_MERGE, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    preMergeStageBody(script, repoUrl, sourceBranch, destinationBranch, repoCredentialsId)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },
                createStage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.instrumentationTestStageBodyAndroid(script,
                            androidTestConfig,
                            instrumentalTestGradleTask,
                            instrumentalTestResultPathDirXml,
                            instrumentalTestResultPathXml,
                            instrumentalTestResultPathDirHtml)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.staticCodeAnalysisStageBody(script)
                },

        ]
        finalizeBody = { finalizeStageBody(this) }
    }
}

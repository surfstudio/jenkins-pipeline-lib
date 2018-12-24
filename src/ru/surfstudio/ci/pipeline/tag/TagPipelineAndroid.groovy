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
package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.android.AndroidUtil
import ru.surfstudio.ci.utils.android.config.AndroidTestConfig
import ru.surfstudio.ci.utils.android.config.AvdConfig

class TagPipelineAndroid extends TagPipeline {

    //required initial configuration
    public keystoreCredentials = "no_credentials"
    public keystorePropertiesCredentials = "no_credentials"

    //other
    def gradleConfigFile = "config.gradle"
    def appVersionNameGradleVar = "versionName"
    def appVersionCodeGradleVar = "versionCode"

    public buildGradleTask = "clean assembleQa assembleRelease"
    public betaUploadGradleTask = "crashlyticsUploadDistributionQa"

    public unitTestGradleTask = "testQaUnitTest"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"

    // buildType, для которого будут выполняться инструментальные тесты
    public androidTestBuildType = "qa"

    public instrumentalTestAssembleGradleTask = "assembleAndroidTest"
    public instrumentalTestResultPathDirXml = "build/outputs/androidTest-results/instrumental"
    public instrumentalTestResultPathDirHtml = "build/reports/androidTests/instrumental"

    /**
     * Функция, возвращающая имя instrumentation runner для запуска инструментальных тестов.
     *
     * Если для всех модулей проекта используется одинаковый instrumentation runner,
     * то функцию можно переопределить следующим образом:
     *
     * pipeline.getTestInstrumentationRunnerName = { script, prefix -> return "androidx.test.runner.AndroidJUnitRunner" }
     */
    public getTestInstrumentationRunnerName = { script, prefix -> return getDefaultTestInstrumentationRunnerName(script, prefix) }

    public AvdConfig avdConfig = new AvdConfig()

    TagPipelineAndroid(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getAndroidNode()

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                createStage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                createStage(VERSION_UPDATE, StageStrategy.SKIP_STAGE) {
                    versionUpdateStageBodyAndroid(script,
                            repoTag,
                            gradleConfigFile,
                            appVersionNameGradleVar,
                            appVersionCodeGradleVar)
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
                createStage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.instrumentationTestStageBodyAndroid(
                            script,
                            avdConfig,
                            androidTestBuildType,
                            getTestInstrumentationRunnerName,
                            new AndroidTestConfig(
                                    instrumentalTestAssembleGradleTask,
                                    instrumentalTestResultPathDirXml,
                                    instrumentalTestResultPathDirHtml
                            )
                    )
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.staticCodeAnalysisStageBody(script)
                },
                createStage(BETA_UPLOAD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    betaUploadWithKeystoreStageBodyAndroid(script,
                            betaUploadGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },
                createStage(VERSION_PUSH, StageStrategy.SKIP_STAGE) {
                    versionPushStageBody(script,
                            repoTag,
                            branchesPatternsForAutoChangeVersion,
                            repoUrl,
                            repoCredentialsId,
                            prepareChangeVersionCommitMessageAndroid(
                                    script,
                                    gradleConfigFile,
                                    appVersionNameGradleVar,
                                    appVersionCodeGradleVar,
                            ))
                },
        ]
        finalizeBody = { finalizeStageBody(this) }
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

    def static versionUpdateStageBodyAndroid(Object script,
                                             String repoTag,
                                             String gradleConfigFile,
                                             String appVersionNameGradleVar,
                                             String appVersionCodeGradleVar) {
        AndroidUtil.changeGradleVariable(script, gradleConfigFile, appVersionNameGradleVar, "\"$repoTag\"")
        def codeStr = AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar)
        def newCodeStr = String.valueOf(Integer.valueOf(codeStr) + 1)
        AndroidUtil.changeGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar, newCodeStr)

    }

    def static prepareChangeVersionCommitMessageAndroid(Object script,
                                                        String gradleConfigFile,
                                                        String appVersionNameGradleVar,
                                                        String appVersionCodeGradleVar){
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionNameGradleVar))
        def versionCode = AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar)
        return "Change version to $versionName ($versionCode) $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }

    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        script.sh "./gradlew ${betaUploadGradleTask}"
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

    /**
     * Функция, возвращающая имя instrumentation runner, которое будет получено с помощью gradle-таска.
     *
     * Пример такого gradle-таска:
     *
     * task printTestInstrumentationRunnerName {
     *     doLast {
     *         println "$android.defaultConfig.testInstrumentationRunner"
     *     }
     * }
     */
    private getDefaultTestInstrumentationRunnerName = { script, prefix ->
        def defaultInstrumentationRunnerGradleTaskName = "printTestInstrumentationRunnerName"
        return script.sh(
                returnStdout: true,
                script: "./gradlew :$prefix:$defaultInstrumentationRunnerGradleTaskName | tail -4 | head -1"
        )
    }
}
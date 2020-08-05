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
import ru.surfstudio.ci.utils.buildsystems.GradleUtil

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

    //required for firebase app distribution
    public firebaseAppDistributionTask = "appDistributionUploadQa"
    public googleServiceAccountCredsId = "surf-jarvis-firebase-token"

    public useFirebaseDistribution = true

    public unitTestGradleTask = "testQaUnitTest -PtestType=unit"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"

    // buildType, для которого будут выполняться инструментальные тесты
    public androidTestBuildType = "qa"

    public instrumentalTestAssembleGradleTask = "assembleAndroidTest"
    public instrumentalTestResultPathDirXml = "build/outputs/androidTest-results/instrumental"
    public instrumentalTestResultPathDirHtml = "build/reports/androidTests/instrumental"

    // флаг, показывающий, должно ли имя AVD быть уникальным для текущего job'a
    public generateUniqueAvdNameForJob = true

    // количество попыток перезапуска тестов для одного модуля при падении одного из них
    public instrumentationTestRetryCount = 0

    /**
     * Функция, возвращающая имя instrumentation runner для запуска инструментальных тестов.
     *
     * Если для всех модулей проекта используется одинаковый instrumentation runner,
     * то функцию можно переопределить следующим образом:
     *
     * pipeline.getTestInstrumentationRunnerName = { script, prefix -> return "androidx.test.runner.AndroidJUnitRunner" }*/
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
                stage(CHECKOUT, false) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(VERSION_UPDATE) {
                    versionUpdateStageBodyAndroid(script,
                            repoTag,
                            gradleConfigFile,
                            appVersionNameGradleVar,
                            appVersionCodeGradleVar)
                },
                stage(BUILD) {
                    AndroidPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },
                stage(UNIT_TEST) {
                    AndroidPipelineHelper.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                stage(INSTRUMENTATION_TEST) {
                    AndroidPipelineHelper.instrumentationTestStageBodyAndroid(
                            script,
                            avdConfig,
                            androidTestBuildType,
                            getTestInstrumentationRunnerName,
                            new AndroidTestConfig(
                                    instrumentalTestAssembleGradleTask,
                                    instrumentalTestResultPathDirXml,
                                    instrumentalTestResultPathDirHtml,
                                    generateUniqueAvdNameForJob,
                                    instrumentationTestRetryCount
                            )
                    )
                },
                stage(STATIC_CODE_ANALYSIS) {
                    AndroidPipelineHelper.staticCodeAnalysisStageBody(script)
                },
                stage(BETA_UPLOAD) {
                    if (useFirebaseDistribution) {
                        firebaseUploadWithKeystoreStageBodyAndroid(
                                script,
                                googleServiceAccountCredsId,
                                firebaseAppDistributionTask,
                                keystoreCredentials,
                                keystorePropertiesCredentials
                        )
                    } else {
                        betaUploadWithKeystoreStageBodyAndroid(
                                script,
                                betaUploadGradleTask,
                                keystoreCredentials,
                                keystorePropertiesCredentials
                        )
                    }

                },
                stage(VERSION_PUSH, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
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
    @Deprecated
    def static betaUploadWithKeystoreStageBodyAndroid(Object script,
                                                      String betaUploadGradleTask,
                                                      String keystoreCredentials,
                                                      String keystorePropertiesCredentials) {
        AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials) {
            betaUploadStageBodyAndroid(script, betaUploadGradleTask)
        }
    }

    def static firebaseUploadWithKeystoreStageBodyAndroid(Object script,
                                                          String googleServiceAccountCredsId,
                                                          String firebaseAppDistributionTask,
                                                          String keystoreCredentials,
                                                          String keystorePropertiesCredentials) {
        AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials) {
            withFirebaseToken(script, googleServiceAccountCredsId) {
                gradleTaskWithBuildCache(script, firebaseAppDistributionTask)
            }
        }
    }

    def static versionUpdateStageBodyAndroid(Object script,
                                             String repoTag,
                                             String gradleConfigFile,
                                             String appVersionNameGradleVar,
                                             String appVersionCodeGradleVar) {
        GradleUtil.changeGradleVariable(script, gradleConfigFile, appVersionNameGradleVar, "\"$repoTag\"")
        def codeStr = GradleUtil.getGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar)
        def newCodeStr = String.valueOf(Integer.valueOf(codeStr) + 1)
        GradleUtil.changeGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar, newCodeStr)

    }

    def static prepareChangeVersionCommitMessageAndroid(Object script,
                                                        String gradleConfigFile,
                                                        String appVersionNameGradleVar,
                                                        String appVersionCodeGradleVar) {
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                GradleUtil.getGradleVariable(script, gradleConfigFile, appVersionNameGradleVar))
        def versionCode = GradleUtil.getGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar)
        return "Change version to $versionName ($versionCode) $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }

    @Deprecated
    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        gradleTaskWithBuildCache(script, betaUploadGradleTask)
    }

    def static withFirebaseToken(Object script, String googleServiceAccountCredsId, Closure body) {
        script.withCredentials([
                script.string(credentialsId: googleServiceAccountCredsId, variable: 'FIREBASE_TOKEN')
        ]) {
            body()
        }
    }

    private def static gradleTaskWithBuildCache(Object script, String gradleTask) {
        GradleUtil.withGradleBuildCacheCredentials(script) {
            script.sh "./gradlew ${gradleTask}"
        }
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
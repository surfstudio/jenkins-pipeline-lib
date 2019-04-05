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
import ru.surfstudio.ci.pipeline.helper.FlutterPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.android.AndroidUtil
import ru.surfstudio.ci.utils.android.config.AndroidTestConfig
import ru.surfstudio.ci.utils.android.config.AvdConfig

class TagPipelineFlutter extends TagPipeline {


    public static final String BUILD_ANDROID = 'Build Android'
    public static final String BUILD_IOS = 'Build iOS'
    public static final String BETA_UPLOAD_ANDROID = 'Beta Upload Android'
    public static final String BETA_UPLOAD_IOS = 'Beta Upload iOS'

    //required initial configuration
    public androidKeystoreCredentials = "no_credentials"
    public androidKeystorePropertiesCredentials = "no_credentials"

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6" //todo
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey" //todo


    public buildAndroidCommand = "flutter build apk --qa --release"
    public buildIOsCommand = "flutter build ios --qa --release"
    public testCommand = "flutter test"

    def configFile = "pubspec.yaml"
    def appVersionNameVar = "version"
    //def appVersionCodeGradleVar = "versionCode"

    TagPipelineFlutter(Object script) {
        super(script)
    }

    def init() {
        node = NodeProvider.flutterNode

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(VERSION_UPDATE) {
                    script.echo "empty"
                    //todo
                    /*versionUpdateStageBodyAndroid(script,
                            repoTag,
                            configFile,
                            appVersionNameVar,
                            appVersionCodeGradleVar)*/
                },
                parallel(BUILD, [
                        stage(BUILD_ANDROID, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                            FlutterPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                                    buildAndroidCommand,
                                    androidKeystoreCredentials,
                                    androidKeystorePropertiesCredentials)
                        },
                        stage(BUILD_IOS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                            FlutterPipelineHelper.buildStageBodyIOS(script,
                                    buildIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)
                        },
                ]),
                createStage(UNIT_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    FlutterPipelineHelper.testStageBody(script, testCommand)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    FlutterPipelineHelper.staticCodeAnalysisStageBody(script)
                },
                parallel(BETA_UPLOAD, [
                        stage(BETA_UPLOAD_ANDROID, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                            script.echo "empty"
                            //todo
                            /*betaUploadWithKeystoreStageBodyAndroid(script,
                                    betaUploadGradleTask,
                                    keystoreCredentials,
                                    keystorePropertiesCredentials)*/
                        },
                        stage(BETA_UPLOAD_IOS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                            script.echo "empty"
                            //todo
                           /* FlutterPipelineHelper.buildStageBodyiOS(script,
                                    buildIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)*/
                        },
                ]),
                stage(VERSION_PUSH) {
                    script.echo "empty"
                    //todo
                   /* versionPushStageBody(script,
                            repoTag,
                            branchesPatternsForAutoChangeVersion,
                            repoUrl,
                            repoCredentialsId,
                            prepareChangeVersionCommitMessage(
                                    script,
                                    gradleConfigFile,
                                    appVersionNameGradleVar,
                                    appVersionCodeGradleVar,
                            ))*/
                },


        ]
        finalizeBody = { finalizeStageBody(this) }
    }



    //other

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

    def static prepareChangeVersionCommitMessage(Object script,
                                                        String gradleConfigFile,
                                                        String appVersionNameGradleVar,
                                                        String appVersionCodeGradleVar){
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionNameGradleVar))
        def versionCode = AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar)
        return "Change version to $versionName ($versionCode) $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }

    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        AndroidUtil.withGradleBuildCacheCredentials(script) {
            script.sh "./gradlew ${betaUploadGradleTask}"
        }
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}
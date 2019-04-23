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
import ru.surfstudio.ci.pipeline.helper.FlutterPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.android.AndroidUtil
import ru.surfstudio.ci.utils.flutter.FlutterUtil

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


    public buildAndroidCommand = "flutter build apk --release -t lib/main-qa.dart && flutter build apk --release -t lib/main-release.dart"
    public buildIOsCommand = "flutter build ios --release -t lib/main-qa.dart && flutter build ios --release -t lib/main-release.dart"
    public testCommand = "flutter test"

    def configFile = "pubspec.yaml"
    def compositeVersionNameVar = "version"

    TagPipelineFlutter(Object script) {
        super(script)
    }

    def init() {
        node = NodeProvider.androidFlutterNode

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(VERSION_UPDATE, StageStrategy.UNDEFINED) {
                    versionUpdateStageBodyAndroid(script,
                            repoTag,
                            configFile,
                            compositeVersionNameVar)
                },
                stage(BUILD_ANDROID) {
                    FlutterPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildAndroidCommand,
                            androidKeystoreCredentials,
                            androidKeystorePropertiesCredentials)
                },
                stage(UNIT_TEST) {
                    FlutterPipelineHelper.testStageBody(script, testCommand)
                },
                stage(STATIC_CODE_ANALYSIS) {
                    FlutterPipelineHelper.staticCodeAnalysisStageBody(script)
                },
                stage(BETA_UPLOAD_ANDROID) {
                    script.echo "empty"
                    //todo
                    /*betaUploadWithKeystoreStageBodyAndroid(script,
                            betaUploadGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)*/
                },
                node(NodeProvider.iOSFlutterNode, true, [
                        stage(BUILD_IOS) {
                            FlutterPipelineHelper.buildStageBodyIOS(script,
                                    buildIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)
                        },
                        stage(BETA_UPLOAD_IOS) {
                            script.echo "empty"
                            //todo
                           /* FlutterPipelineHelper.buildStageBodyiOS(script,
                                    buildIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)*/
                        },
                ]),
                stage(VERSION_PUSH, StageStrategy.UNDEFINED) {
                   versionPushStageBody(script,
                            repoTag,
                            branchesPatternsForAutoChangeVersion,
                            repoUrl,
                            repoCredentialsId,
                            prepareChangeVersionCommitMessage(
                                    script,
                                    configFile,
                                    compositeVersionNameVar
                            ))
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
                                             String configYamlFile,
                                             String compositeVersionNameVar) {
        def compositeVersion = FlutterUtil.getYamlVariable(script, configYamlFile, compositeVersionNameVar)
        def versionCode = FlutterUtil.getVersionCode(compositeVersion)
        def newVersionCode = String.valueOf(Integer.valueOf(versionCode) + 1)
        def newCompositeVersion = "$repoTag+$newVersionCode"
        FlutterUtil.changeYamlVariable(script, configYamlFile, compositeVersionNameVar, newCompositeVersion)
    }

    def static prepareChangeVersionCommitMessage(Object script,
                                                 String configYamlFile,
                                                 String compositeVersionNameVar){
        def compositeVersion = FlutterUtil.getYamlVariable(script, configYamlFile, compositeVersionNameVar)
        return "Change version to $compositeVersion $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }

    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        AndroidUtil.withGradleBuildCacheCredentials(script) {
            script.sh "./gradlew ${betaUploadGradleTask}"
        }
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}
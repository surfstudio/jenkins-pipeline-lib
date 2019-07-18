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


import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.FlutterPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.StageWithStrategy
import ru.surfstudio.ci.utils.flutter.FlutterUtil
import ru.surfstudio.ci.CommonUtil

import static ru.surfstudio.ci.CommonUtil.extractValueFromParamsAndRun

class TagPipelineFlutter extends TagPipeline {

    public static final String CALCULATE_VERSION_CODES = 'Calculate Version Codes'
    public static final String VERSION_UPDATE_FOR_ARM64 = 'Version Update For Arm64'
    public static final String BUILD_ANDROID = 'Build Android'
    public static final String BUILD_ANDROID_ARM64 = 'Build Android Arm64'
    public static final String BETA_UPLOAD_ANDROID = 'Beta Upload Android'
    public static final String BUILD_IOS_BETA = 'Build iOS BETA'
    public static final String BUILD_IOS_TESTFLIGHT = 'Build iOS TestFlight'
    public static final String BETA_UPLOAD_IOS = 'Beta Upload iOS'
    public static final String TESTFLIGHT_UPLOAD_IOS = 'TestFlight Upload iOS'

    //required initial configuration
    public androidKeystoreCredentials = "no_credentials"
    public androidKeystorePropertiesCredentials = "no_credentials"


    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "AppleIDDev.p12"

    //build flags
    public boolean shouldBuildAndroid = true
    public boolean shouldBuildIosBeta = true
    public boolean shouldBuildIosTestFlight = false

    public buildAndroidCommand = "./script/android/build.sh -qa " +
            "&& ./script/android/build.sh -release "
    /*public buildAndroidCommandArm64 = "./script/android/build.sh -qa -x64 " +
            "&& ./script/android/build.sh -release -x64"*/
    public buildQaIOsCommand = "./script/ios/build.sh -qa"
    public buildReleaseIOsCommand = "./script/ios/build.sh -release"
    public testCommand = "flutter test"

    public configFile = "pubspec.yaml"
    public compositeVersionNameVar = "version"

    public shBetaUploadCommandAndroid = "cd android && fastlane android beta"
    public shBetaUploadCommandIos = "make -C ios/ beta"
    public shTestFlightUploadCommandIos = "make -C ios/ release"

    //versions
    public minVersionCode = 10000
    public mainVersionCode = "<undefined>"
    public arm64VersionCode = "<undefined>"

    TagPipelineFlutter(Object script) {
        super(script)
    }

    def init() {

        node = NodeProvider.androidFlutterNode

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = { initBodyFlutter(this) }
        propertiesProvider = {
            [
                    buildDiscarder(script),
                    parametersFlutter(script),
                    triggers(script, this.repoUrl, this.tagRegexp)
            ]
        }

        stages = [
                stage(CHECKOUT, false) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(CALCULATE_VERSION_CODES) {
                    calculateVersionCodesStageBody(this,
                            configFile,
                            compositeVersionNameVar,
                            minVersionCode)
                },
//                stage(VERSION_UPDATE_FOR_ARM64) {
//                    versionUpdateStageBody(script,
//                            repoTag,
//                            arm64VersionCode,
//                            configFile,
//                            compositeVersionNameVar)
//                },
//                stage(BUILD_ANDROID_ARM64) {
//                    FlutterPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
//                            buildAndroidCommandArm64,
//                            androidKeystoreCredentials,
//                            androidKeystorePropertiesCredentials)
//                },
                stage(VERSION_UPDATE) {
                    versionUpdateStageBody(script,
                            repoTag,
                            mainVersionCode,
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
                    uploadStageBody(script, shBetaUploadCommandAndroid)
                },
                node(NodeProvider.iOSFlutterNode, true, [
                        stage(BUILD_IOS_BETA) {
                            FlutterPipelineHelper.buildStageBodyIOS(script,
                                    buildQaIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)
                        },
                        stage(BETA_UPLOAD_IOS) {
                            uploadStageBody(script, shBetaUploadCommandIos)
                        },
                        stage(BUILD_IOS_TESTFLIGHT) {
                            FlutterPipelineHelper.buildStageBodyIOS(script,
                                    buildReleaseIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)
                        },
                        stage(TESTFLIGHT_UPLOAD_IOS) {
                            uploadStageBody(script, shTestFlightUploadCommandIos)
                        }
                ]),
                stage(VERSION_PUSH, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
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


    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ ======================================================
    private static initBodyFlutter(TagPipelineFlutter ctx) {
        initBody(ctx)

        def script = ctx.script
        extractValueFromParamsAndRun(script, PARAMETER_ANDROID_FULL_BETA) { value ->
            ctx.shouldBuildAndroid = value
            script.echo "Android full build with upload to Beta(qa) : $ctx.shouldBuildAndroid"
        }
        extractValueFromParamsAndRun(script, PARAMETER_IOS_FOR_BETA) { value ->
            ctx.shouldBuildIosBeta = value
            script.echo "Ios qa build with upload to Beta(qa) : $ctx.shouldBuildIosBeta | extracted $value "

        }
        extractValueFromParamsAndRun(script, PARAMETER_IOS_FOR_TESTFLIGHT) { value ->
            ctx.shouldBuildIosTestFlight = value
            script.echo "Ios release build with upload to TestFlight(release) : ${ctx.shouldBuildIosTestFlight}"

        }

        initStrategies(ctx)
    }

    private static void initStrategies(TagPipelineFlutter ctx) {
        (ctx.getStage(BUILD_ANDROID) as StageWithStrategy).strategy = ctx.shouldBuildAndroid ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE
        (ctx.getStage(BUILD_ANDROID_ARM64) as StageWithStrategy).strategy = ctx.shouldBuildAndroid ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE
        (ctx.getStage(BETA_UPLOAD_ANDROID) as StageWithStrategy).strategy = ctx.shouldBuildAndroid ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE

        (ctx.getStage(BUILD_IOS_BETA) as StageWithStrategy).strategy = ctx.shouldBuildIosBeta ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE
        (ctx.getStage(BETA_UPLOAD_IOS) as StageWithStrategy).strategy = ctx.shouldBuildIosBeta ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE

        (ctx.getStage(BUILD_IOS_TESTFLIGHT) as StageWithStrategy).strategy = ctx.shouldBuildIosTestFlight ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE
        (ctx.getStage(TESTFLIGHT_UPLOAD_IOS) as StageWithStrategy).strategy = ctx.shouldBuildIosTestFlight ? DEFAULT_STAGE_STRATEGY : StageStrategy.SKIP_STAGE
    }

    def static calculateVersionCodesStageBody(TagPipelineFlutter ctx,
                                              String configFile,
                                              String compositeVersionNameVar,
                                              Integer minVersionCode) {
        def script = ctx.script
        def compositeVersion = FlutterUtil.getYamlVariable(script, configFile, compositeVersionNameVar)
        def versionCode = Integer.valueOf(FlutterUtil.getVersionCode(compositeVersion))
        def newMainVersionCode = versionCode + 1
        if (newMainVersionCode < minVersionCode) {
            newMainVersionCode = minVersionCode
        }
        ctx.mainVersionCode = String.valueOf(newMainVersionCode)
//        ctx.arm64VersionCode = "64" + String.valueOf(newMainVersionCode)
        script.echo "New main versionCode: $ctx.mainVersionCode"
//        script.echo "New arm64 versionCode: $ctx.arm64VersionCode"
    }

    def static uploadStageBody(Object script, String shBetaUploadCommand) {
        CommonUtil.shWithRuby(script, shBetaUploadCommand)
    }

    def static versionUpdateStageBody(Object script,
                                      String repoTag,
                                      String versionCode,
                                      String configYamlFile,
                                      String compositeVersionNameVar) {
        def newCompositeVersion = "$repoTag+$versionCode"
        FlutterUtil.changeYamlVariable(script, configYamlFile, compositeVersionNameVar, newCompositeVersion)
    }

    def static prepareChangeVersionCommitMessage(Object script,
                                                 String configYamlFile,
                                                 String compositeVersionNameVar) {
        def compositeVersion = FlutterUtil.getYamlVariable(script, configYamlFile, compositeVersionNameVar)
        return "Change version to $compositeVersion $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

    // ======================================================  ↓↓↓ PARAMETERS ↓↓↓   ====================================================
    public static final String PARAMETER_ANDROID_FULL_BETA = 'parameterAndroidFullBeta'
    public static final String PARAMETER_IOS_FOR_BETA = 'parameterIosForBeta'
    public static final String PARAMETER_IOS_FOR_TESTFLIGHT = 'parameterIosForTestFlight'

    public static final String RELEASE_TYPE = "release"
    public static final String QA_BUILD_TYPE = "qa"

    def static parametersFlutter(script) {
        return script.parameters([
                [
                        $class       : 'GitParameterDefinition',
                        name         : REPO_TAG_PARAMETER,
                        type         : 'PT_TAG',
                        description  : 'Тег для сборки',
                        selectedValue: 'NONE',
                        sortMode     : 'DESCENDING_SMART'
                ],
                script.booleanParam(
                        defaultValue: true,
                        name: PARAMETER_ANDROID_FULL_BETA,
                        description: "Сборка Android(qa/release). Qa выгружается в Beta",
                ),
                script.booleanParam(
                        defaultValue: true,
                        name: PARAMETER_IOS_FOR_BETA,
                        description: "Сборка Ios(qa). Qa выгружается в Beta",
                ),
                script.booleanParam(
                        defaultValue: false,
                        name: PARAMETER_IOS_FOR_TESTFLIGHT,
                        description: "Сборка Ios(release). Release выгружается в TestFlight",
                ),
                script.string(
                        name: UNIT_TEST_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),
                script.string(
                        name: INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),
                script.string(
                        name: STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),
                script.string(
                        name: BETA_UPLOAD_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),

        ])
    }

}
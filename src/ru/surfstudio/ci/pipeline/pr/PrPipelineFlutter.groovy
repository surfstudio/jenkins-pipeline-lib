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
import ru.surfstudio.ci.pipeline.helper.FlutterPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy

class PrPipelineFlutter extends PrPipeline {
    public static final String STAGE_ANDROID = 'Stage Android'
    public static final String STAGE_IOS = 'Stage IOS'

    public static final String CHECKOUT_FLUTTER_VERSION = 'Checkout Flutter Project Version'
    public static final String BUILD_ANDROID = 'Build Android'
    public static final String BUILD_IOS = 'Build iOS'

    //required initial configuration
    public androidKeystoreCredentials = "no_credentials"
    public androidKeystorePropertiesCredentials = "no_credentials"

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "SurfDevelopmentPrivateKey"

    //sh commands
    public checkoutFlutterVersionCommand = "./script/version.sh"

    public buildAndroidCommand = "./script/android/build.sh -qa && ./script/android/build.sh -qa -x64"
    public buildIOsCommand = "./script/ios/build.sh -qa"
    public testCommand = "flutter test"


    PrPipelineFlutter(Object script) {
        super(script)
    }

    def init() {
        node = NodeProvider.androidFlutterNode

        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }


        def androidStages = [
                stage(STAGE_ANDROID, false) {
                    // todo it's a dirty hack from this comment https://issues.jenkins-ci.org/browse/JENKINS-53162?focusedCommentId=352174&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-352174
                },
                stage(CHECKOUT, false) {
                    checkout(script, repoUrl, sourceBranch, repoCredentialsId)
                    saveCommitHashAndCheckSkipCi(script, targetBranchChanged)
                    abortDuplicateBuildsWithDescription(this)
                },
                stage(PRE_MERGE, false) {
                    preMergeStageBody(script, repoUrl, sourceBranch, destinationBranch, repoCredentialsId)
                },
                stage(CHECKOUT_FLUTTER_VERSION) {
                    script.sh checkoutFlutterVersionCommand
                },
                stage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    FlutterPipelineHelper.staticCodeAnalysisStageBody(script)
                },
                stage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    FlutterPipelineHelper.testStageBody(script, testCommand)
                },
                stage(BUILD_ANDROID) {
                    FlutterPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildAndroidCommand,
                            androidKeystoreCredentials,
                            androidKeystorePropertiesCredentials)
                },
        ]

        def iosStages = [
                stage(STAGE_IOS, false) {
                    // todo it's a dirty hack from this comment https://issues.jenkins-ci.org/browse/JENKINS-53162?focusedCommentId=352174&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-352174
                },
                stage(PRE_MERGE, false) {
                    preMergeStageBody(script, repoUrl, sourceBranch, destinationBranch, repoCredentialsId)
                },
                stage(CHECKOUT_FLUTTER_VERSION) {
                    script.sh checkoutFlutterVersionCommand
                },
                stage(BUILD_IOS) {
                    FlutterPipelineHelper.buildStageBodyIOS(script,
                            buildIOsCommand,
                            iOSKeychainCredenialId,
                            iOSCertfileCredentialId)
                },
        ]

        stages = [
                parallel('Parallel build', [
                        group(STAGE_ANDROID, androidStages),
                        node(STAGE_IOS, NodeProvider.iOSFlutterNode, false, iosStages)
                ]),
        ]

        finalizeBody = { finalizeStageBody(this) }
    }
}

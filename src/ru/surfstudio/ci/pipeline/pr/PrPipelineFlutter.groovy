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

    public static final String BUILD_ANDROID = 'Build Android'
    public static final String BUILD_IOS = 'Build iOS'

    //required initial configuration
    public androidKeystoreCredentials = "no_credentials"
    public androidKeystorePropertiesCredentials = "no_credentials"

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    //sh commands
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

        initializeBody = {
            initBody(this)
            abortDuplicateBuildsWithDescription(this)
        }
        propertiesProvider = { properties(this) }

        stages = [
                stage(PRE_MERGE, false) {
                    checkout(script, repoUrl, sourceBranch, repoCredentialsId)
                    mergeLocal(script, destinationBranch)
                    saveCommitHashAndCheckSkipCi(script, targetBranchChanged)
                },
                stage(BUILD_ANDROID) {
                    FlutterPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildAndroidCommand,
                            androidKeystoreCredentials,
                            androidKeystorePropertiesCredentials)
                },
                node(NodeProvider.iOSFlutterNode, true, [
                        stage(BUILD_IOS) {
                            FlutterPipelineHelper.buildStageBodyIOS(script,
                                    buildIOsCommand,
                                    iOSKeychainCredenialId,
                                    iOSCertfileCredentialId)
                        },
                ]),
                stage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    FlutterPipelineHelper.testStageBody(script, testCommand)
                },
                stage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    FlutterPipelineHelper.staticCodeAnalysisStageBody(script)
                },

        ]
        finalizeBody = { finalizeStageBody(this) }
    }
}

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
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.helper.FlutterPipelineHelper
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.SimpleStage
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.CommonUtil

class PrPipelineFlutter extends PrPipeline {

    public static final String STAGE_PARALLEL = 'Parallel Pipeline'

    public static final String STAGE_DOCKER = 'Docker Flutter'
    
    
    public static final String STAGE_ANDROID = 'Android'
    public static final String STAGE_IOS = 'IOS'

    public static PRE_MERGE_IOS = 'PreMerge IOS'

    public static final String CHECKOUT_FLUTTER_VERSION_ANDROID = 'Checkout Flutter Project Version (Android)'
    public static final String CHECKOUT_FLUTTER_VERSION_IOS = 'Checkout Flutter Project Version (iOS)'

    public static final String BUILD_ANDROID = 'Build Android'
    public static final String BUILD_IOS = 'Build iOS'

    //required initial configuration
    public androidKeystoreCredentials = 'no_credentials'
    public androidKeystorePropertiesCredentials = 'no_credentials'

    public iOSKeychainCredenialId = 'add420b4-78fc-4db0-95e9-eeb0eac780f6'
    public iOSCertfileCredentialId = 'SurfDevelopmentPrivateKey'

    //sh commands
    public checkoutFlutterVersionCommand = './script/version.sh' // ios only

    public buildAndroidCommand = './script/android/build.sh -qa && ./script/android/build.sh -qa -x64'
    public buildIOsCommand = './script/ios/build.sh -qa'
    public testCommand = 'flutter test'

    //nodes
    public nodeIos
    public nodeAndroid

    //docker
    
    //
    // Чтобы изменить канал Flutter для сборки проекта
    // необходимо в конфиге нужного job'a (лежит в мастер ветке проекта)
    // переопределить это поле и изменить тег образа на название
    // нужного канала (stable, beta или dev). Например:
    //
    // def pipeline = new PrPipelineFlutter(this)
    // pipeline.dockerImageName = "cirrusci/flutter:dev"
    
    //
    public dockerImageName = 'cirrusci/flutter:stable'
    
    public dockerArguments = "-it -v \${PWD}:/build --workdir /build"
    

    PrPipelineFlutter(Object script) {
        super(script)
    }

    def init() {
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
                stage(PRE_MERGE_IOS, false) {
                    preMergeStageBody(script, repoUrl, sourceBranch, destinationBranch, repoCredentialsId)
                },
                stage(CHECKOUT_FLUTTER_VERSION_IOS) {
                    script.sh checkoutFlutterVersionCommand
                },
                stage(BUILD_IOS) {
                    FlutterPipelineHelper.buildStageBodyIOS(script,
                            buildIOsCommand,
                            iOSKeychainCredenialId,
                            iOSCertfileCredentialId)
                },
        ]

        node = 'master'
        nodeAndroid = nodeAndroid ?: NodeProvider.androidFlutterNode
        nodeIos = nodeIos ?: NodeProvider.iOSFlutterNode

        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = {
            initBody(this)

            if (this.targetBranchChanged) {
                script.echo 'Build triggered by target branch changes, skip IOS branch'
                stages = [ node(STAGE_ANDROID, nodeAndroid, false, androidStages) ]
            }
        }
        propertiesProvider = { properties(this) }

        stages = [
                parallel(STAGE_PARALLEL, [
                    node(STAGE_ANDROID, nodeAndroid, false, [
                        docker(STAGE_DOCKER, dockerImageName, dockerArguments, androidStages)
                        ]
                    ),
                        node(STAGE_IOS, nodeIos , false, iosStages)
                ]),
        ]

        finalizeBody = { finalizeStageBody(this) }
    }

    def run() {
        CommonUtil.fixVisualizingStagesInParallelBlock(script)
        try {
            def initStage = stage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR, false, createInitStageBody())
            initStage.execute(script, this)
            if (CommonUtil.notEmpty(node)) {
                script.echo "Switch to node ${node}: ${script.env.NODE_NAME}"
            }
            for (Stage stage : stages) {
                stage.execute(script, this)
            }
        } finally {
            jobResult = calculateJobResult(stages)
            if (jobResult == Result.ABORTED || jobResult == Result.FAILURE) {
                script.echo "Job stopped, see reason above ^^^^"
            }
            script.echo "Finalize build:"
            printStageResults()
            script.echo "Current job result: ${script.currentBuild.result}"
            script.echo "Try apply job result: ${jobResult}"
            script.currentBuild.result = jobResult
            //нельзя повышать статус, то есть если раньше был установлен failed или unstable, нельзя заменить на success
            script.echo "Updated job result: ${script.currentBuild.result}"
            if (finalizeBody) {
                script.echo "Start finalize body"
                finalizeBody()
                script.echo "End finalize body"
            }
        }
    }
}

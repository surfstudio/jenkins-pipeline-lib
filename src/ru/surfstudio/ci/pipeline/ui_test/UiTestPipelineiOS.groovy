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
package ru.surfstudio.ci.pipeline.ui_test

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy

class UiTestPipelineiOS extends UiTestPipeline {


    //dirs
    public derivedDataPath = "${sourcesDir}"

    //files
    public simulatorIdentificationFile = "currentSim"

    //environment
    public testDeviceName = "iPhone 7"
    public testOSVersion = "12.1"
    public testiOSSDK = "iphonesimulator12.1"


    UiTestPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def init() {
        platform = "ios"
        node = NodeProvider.getiOSNode()

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                createStage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    initBody(this)
                },
                createStage(CHECKOUT_SOURCES, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutSourcesBody(script, sourcesDir, sourceRepoUrl, sourceBranch, sourceRepoCredentialsId)
                },
                createStage(CHECKOUT_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutTestsStageBody(script, repoUrl, testBranch, testRepoCredentialsId)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    buildStageBodyiOS(script,
                            sourcesDir, 
                            derivedDataPath,
                            testiOSSDK,
                            iOSKeychainCredenialId, 
                            iOSCertfileCredentialId)
                },
                createStage(PREPARE_ARTIFACT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    script.echo "empty stage"
                },
                createStage(PREPARE_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    prepareTestsStageBody(script,
                            jiraAuthenticationName,
                            taskKey,
                            featuresDir,
                            featureForTest)
                },
                createStage(TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    testStageBodyiOS(script,
                            taskKey,
                            sourcesDir,
                            derivedDataPath,
                            testDeviceName,
                            testOSVersion,
                            outputsDir,
                            featuresDir,
                            featureForTest,
                            outputHtmlFile,
                            outputJsonFile)
                },
                createStage(PUBLISH_RESULTS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    publishResultsStageBody(script,
                            outputsDir,
                            outputJsonFile,
                            outputHtmlFile,
                            jiraAuthenticationName,
                            "UI Tests ${taskKey} ${taskName}")

                }

        ]
        finalizeBody = { finalizeStageBody(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static buildStageBodyiOS(Object script, String sourcesDir, String derivedDataPath, String sdk, String keychainCredenialId, String certfileCredentialId) {
        script.withCredentials([
                script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
                script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
        ]) {
            script.sh 'security -v unlock-keychain -p $KEYCHAIN_PASS'
            script.sh 'security import "$DEVELOPER_P12_KEY" -P ""'

            CommonUtil.shWithRuby(script, "gem install bundler -v 1.17.3")

            script.dir(sourcesDir) {
                CommonUtil.shWithRuby(script, "make init")
            }
            CommonUtil.shWithRuby(script, "bundle install")
            CommonUtil.shWithRuby(script, "echo -ne '\n' | bundle exec calabash-ios setup ${sourcesDir}")
            script.sh "cd .. && ./calabash-expect.sh \"MDKTests\" \"MDK Debug\""

            script.sh "xcodebuild -workspace ${sourcesDir}/*.xcworkspace -scheme \$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//') -allowProvisioningUpdates -sdk ${sdk} -derivedDataPath ${derivedDataPath}"
        }
    }

    def static testStageBodyiOS(Object script,
                                String taskKey,
                                String sourcesDir,
                                String derivedDataPath,
                                String device,
                                String iosVersion,
                                String outputsDir,
                                String featuresDir,
                                String featureFile,
                                String outputHtmlFile,
                                String outputJsonFile) {

        def simulatorIdentifierFile = "currentsim"

        script.sh "xcrun simctl shutdown all"

        script.echo "Setting up simulator ..."
        script.sh "xcrun simctl create \"MyTestiPhone\" \"${device}\" \"${iosVersion}\" > ${simulatorIdentifierFile}"
        script.sh "xcrun simctl list"


        script.sh "xcrun simctl boot \$(cat ${simulatorIdentifierFile})"
        script.sh "xcrun simctl install booted ${derivedDataPath}/Build/Products/Debug-iphonesimulator/*.app"

        script.echo "Tests started"
        script.echo "start tests for $taskKey"
        CommonUtil.safe(script) {
            script.sh "mkdir $outputsDir"
        }


        try {
            CommonUtil.shWithRuby(script, "APP_BUNDLE_PATH=${derivedDataPath}/Build/Products/Debug-iphonesimulator/\$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//').app DEVICE_TARGET=\$(cat ${simulatorIdentifierFile}) bundle exec cucumber -p ios ${featuresDir}/${featureFile} -f html -o ${outputsDir}/${outputHtmlFile} -f json -o ${outputsDir}/${outputJsonFile} -f pretty")
        } finally {
            script.sh "xcrun simctl shutdown \$(cat ${simulatorIdentifierFile})"
            script.sh "xcrun simctl shutdown all"
            script.sh "xcrun simctl list"
            script.sh "sleep 15"
            script.echo "Removing simulator ..."

            //script.sh "xcrun simctl delete \$(cat ${simulatorIdentifierFile})"
        }
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}

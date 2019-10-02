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
    public simulatorIdentifierFile = "currentSim"

    //environment
    public testDeviceName = "iPhone 7"
    public testOSVersion = "com.apple.CoreSimulator.SimRuntime.iOS-12-2"
    public testiOSSDK = "iphonesimulator12.2"


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
                            outputJsonFile,
                            outputrerunTxtFile)
                },
                createStage(PUBLISH_RESULTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    publishResultsStageBody(script,
                            outputsDir,
                            outputJsonFile,
                            outputHtmlFile,
                            outputrerunTxtFile,
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
            
            //script.dir(sourcesDir) {
            //    CommonUtil.shWithRuby(script, "bundle install")
            //}
            
            script.dir(sourcesDir) { 
            
                 CommonUtil.safe(script) 
                {

                    CommonUtil.shWithRuby(script, "make set_token_for_snack")
                }
            }

            CommonUtil.shWithRuby(script, "set -x; expect -f calabash-expect.sh; set +x;")

            CommonUtil.safe(script) 
            {

                CommonUtil.shWithRuby(script, "ruby ${sourcesDir}/scripts/flatter.rb ${sourcesDir}/*.xcodeproj")
            }
            
            script.sh "xcodebuild -workspace ${sourcesDir}/*.xcworkspace -scheme \"\$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//')\" -allowProvisioningUpdates -sdk ${sdk} -derivedDataPath ${derivedDataPath}"
            
            
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
                                String outputJsonFile,
                                outputrerunTxtFile) {

        script.lock("Lock_ui_test_on_${script.env.NODE_NAME}") {
            def simulatorIdentifierFile = "currentsim"

            script.sh "xcrun simctl shutdown all"
            script.sh "xcrun simctl erase all"

            script.echo "Setting up simulator ..."
            script.sh "xcrun simctl create \"MyTestiPhone\" \"${device}\" \"${iosVersion}\" > ${simulatorIdentifierFile}"
            script.sh "xcrun simctl list"


            script.sh "xcrun simctl boot \$(cat ${simulatorIdentifierFile})"
            script.sh "xcrun simctl install booted ${derivedDataPath}/Build/Products/Debug-iphonesimulator/*.app"

            CommonUtil.shWithRuby(script, "run-loop simctl manage-processes") 
            script.echo "Tests started"
            script.echo "start tests for $taskKey"
            CommonUtil.safe(script) {
                script.sh "mkdir $outputsDir"
            }


            try {
                CommonUtil.shWithRuby(script, "APP_BUNDLE_PATH=${derivedDataPath}/Build/Products/Debug-iphonesimulator/\$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//').app DEVICE_TARGET=\$(cat ${simulatorIdentifierFile}) bundle exec cucumber -p ios ${featuresDir}/${featureFile} -f rerun -o ${outputsDir}/${outputrerunTxtFile} -f html -o ${outputsDir}/${outputHtmlFile} -f json -o ${outputsDir}/${outputJsonFile} -f pretty")
            } finally {
                script.sh "xcrun simctl shutdown \$(cat ${simulatorIdentifierFile})"
                script.sh "xcrun simctl shutdown all"
                script.sh "xcrun simctl list"
                script.sh "xcrun simctl delete \$(cat ${simulatorIdentifierFile})"
                script.echo "Removing simulator ..."
                script.sh "sleep 3"
    
                 CommonUtil.safe(script) {
                    script.sh "mkdir arhive"
                }
                script.sh "find ${outputsDir} -iname '*.json'; cd ${outputsDir}; mv *.json ../arhive; cd ..; zip -r arhive.zip arhive "
            }
        }
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}

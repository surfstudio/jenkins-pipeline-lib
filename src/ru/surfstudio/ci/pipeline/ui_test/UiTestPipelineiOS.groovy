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
    public testOSVersion = "com.apple.CoreSimulator.SimRuntime.iOS-12-3"
    public testiOSSDK = "iphonesimulator12.3"

    public appZip = "Build-cal.app.zip"
    public app = "Build-cal.app"



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
                stage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    initBody(this)
                },
                stage(CHECKOUT_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                 
                     CommonUtil.safe(script) {
                            script.sh "rm -rf ./*"
                    checkoutTestsStageBody(script, repoUrl, testBranch, testRepoCredentialsId)
                    }
                },
                stage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    buildStageBodyiOS(script,
                            projectForBuild, 
                            appZip, 
                            app)
                },
                stage(PREPARE_ARTIFACT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    script.echo "empty stage"
                },
                stage(PREPARE_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    prepareTestsStageBody(script,
                            jiraAuthenticationName,
                            taskKey,
                            featuresDir,
                            featureForTest)
                },
                stage(TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    testStageBodyiOS(script,
                            taskKey,
                            sourcesDir,
                            derivedDataPath,
                            app,
                            testDeviceName,
                            testOSVersion,
                            outputsDir,
                            featuresDir,
                            featureForTest,
                            outputHtmlFile,
                            outputJsonFile,
                            outputrerunTxtFile,
                            failedStepsFile)
                },
                stage(PUBLISH_RESULTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
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

    def static buildStageBodyiOS(Object script, String projectForBuild, String appZip, String app) {
            script.sh "rm -rf ${app}"
               script.step ([$class: 'CopyArtifact',
                   projectName: "${projectForBuild}",
                    filter: "*-cal.app.zip",
                   target: "."])
    
            script.sh "unzip ${appZip}"

                     
             
        
    }

    def static testStageBodyiOS(Object script,
                                String taskKey,
                                String sourcesDir,
                                String derivedDataPath,
                                String app,
                                String device,
                                String iosVersion,
                                String outputsDir,
                                String featuresDir,
                                String featureFile,
                                String outputHtmlFile,
                                String outputJsonFile,
                                String outputrerunTxtFile,
                                String failedStepsFile) {

        script.lock("Lock_ui_test_on_${script.env.NODE_NAME}") {
            def simulatorIdentifierFile = "currentsim"

            script.sh "xcrun simctl shutdown all"
            script.sh "xcrun simctl erase all"

            script.echo "Setting up simulator ..."
            script.sh "xcrun simctl create \"MyTestiPhone\" \"${device}\" \"${iosVersion}\" > ${simulatorIdentifierFile}"
            script.sh "xcrun simctl list"


            script.sh "xcrun simctl boot \$(cat ${simulatorIdentifierFile})"
            script.sh "xcrun simctl install booted ${app}"

            CommonUtil.shWithRuby(script, "run-loop simctl manage-processes") 
            script.echo "Tests started"
            script.echo "start tests for $taskKey"
            CommonUtil.safe(script) {
                script.sh "mkdir $outputsDir"
            }


            try {
                CommonUtil.shWithRuby(script, "bundle install")
                CommonUtil.shWithRuby(script, "APP_BUNDLE_PATH=${app} DEVICE_TARGET=\$(cat ${simulatorIdentifierFile}) bundle exec cucumber -p ios ${featuresDir}/${featureFile} -f rerun -o ${outputsDir}/${outputrerunTxtFile} -f html -o ${outputsDir}/${outputHtmlFile} -f json -o ${outputsDir}/${outputJsonFile} -f pretty")
                script.sh "xcrun simctl shutdown \$(cat ${simulatorIdentifierFile})"
                script.sh "xcrun simctl shutdown all"
            } finally {
                
                script.sh "xcrun simctl list"
                script.sh "xcrun simctl delete \$(cat ${simulatorIdentifierFile})"
                script.echo "Removing simulator ..."
                script.sh "sleep 3"
    
                 CommonUtil.safe(script) {
                    script.sh "mkdir arhive"
                }

                CommonUtil.shWithRuby(script, "ruby -r \'./group_steps.rb\' -e \"GroupScenarios.new.group_failed_scenarios(\'${outputsDir}/output.json\', \'${failedStepsFile}\')\"")
                script.step([$class: 'ArtifactArchiver', artifacts: failedStepsFile, allowEmptyArchive: true])
               
                script.sh "find ${outputsDir} -iname '*.json'; cd ${outputsDir}; mv *.json ../arhive; cd ..; zip -r arhive.zip arhive "
                
                /* script.build job: 'Labirint_iOS_TAG_Calabash', parameters: [
                    script.string(name: 'taskKey', value: 'LABIOS-2903'),
                    script.string(name: 'testBranch', value: '1.8_Новая_товарка'),
                    script.string(name: 'sourceBranch', value: '<undefined>'),
                    script.string(name: 'userEmail', value: 'tolubaev@surfstudio.ru'),
                    script.string(name: 'projectForBuild', value: 'test'),
                    script.string(name: 'node', value: 'ios-ui-test')] */
            }
        }
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}

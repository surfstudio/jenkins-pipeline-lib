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

class UiTestPipelineAndroid extends UiTestPipeline {

    public artifactForTest = "for_test.apk"
    public buildGradleTask = "clean assembleDebug"
    public builtApkPattern = "${sourcesDir}/**/**.apk"

    UiTestPipelineAndroid(Object script) {
        super(script)
    }

    @Override
    def init() {
        platform = "android"
        node = NodeProvider.getAndroidNode()

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutTestsStageBody(script, repoUrl, testBranch, testRepoCredentialsId)
                },
                stage(CHECKOUT_SOURCES, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutSourcesBody(script, sourcesDir, sourceRepoUrl, sourceBranch, sourceRepoCredentialsId)
                },
                stage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    buildStageBodyAndroid(script, sourcesDir, buildGradleTask)
                },
                stage(PREPARE_ARTIFACT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    prepareApkStageBodyAndroid(script,
                            builtApkPattern,
                            artifactForTest)
                },
                stage(PREPARE_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    prepareTestsStageBody(script,
                            jiraAuthenticationName,
                            taskKey,
                            featuresDir,
                            featureForTest)
                },
                stage(TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    testStageBodyAndroid(script,
                            taskKey,
                            sourcesDir,
                            outputsDir,
                            featuresDir,
                            artifactForTest,
                            featureForTest,
                            outputJsonFile,
                            outputHtmlFile,
                            outputrerunTxtFile,
                            outputsIdsDiff)
                },
                stage(PUBLISH_RESULTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    publishResultsStageBody(script,
                            outputsDir,
                            outputJsonFile,
                            outputHtmlFile,
                            outputrerunTxtFile,
                            jiraAuthenticationName,
                            "UI Tests ${taskKey} ${taskName}",
                            failedStepsFile)

                }

        ]
        finalizeBody = { finalizeStageBody(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static buildStageBodyAndroid(Object script, String sourcesDir, String buildGradleTask) {
     
            
            
            
            //script.sh "./gradlew ${buildGradleTask}"
            //def built = build('Labirint_Android_TAG');  // https://plugins.jenkins.io/pipeline-build-step
           //TODO copyArtifacts(projectName: 'Labirint_Android_TAG', selector: specific("${built.lastSuccessful}"), selector:specific("${built = qa}"); androidTestBuildType = "qa"
            //copyArtifacts(projectName: 'Labirint_Android_TAG', selector: specific("${built.lastSuccessful}")); 

             script.dir(sourcesDir) {
                 
                script.step ([$class: 'CopyArtifact',
                    projectName: 'Labirint_Android_TAG',
                    filter: "*LabirintAndroid*"]);
                    //target: "${sourcesDir}"]);
            }
        }
    

 

    def static prepareApkStageBodyAndroid(Object script, String builtApkPattern, String newApkForTest) {
        script.step([$class: 'ArtifactArchiver', artifacts: builtApkPattern])

        def files = script.findFiles(glob: builtApkPattern)
        String foundedApks = files.join("\n")
        script.echo "founded apks: $foundedApks"
        def apkPath = files[0].path
        script.echo "use first: $apkPath"

        script.sh "mv \"${apkPath}\" ${newApkForTest}"
    }

    def static testStageBodyAndroid(Object script,
                                    String taskKey,
                                    String sourcesDir,
                                    String outputsDir,
                                    String featuresDir,
                                    String artifactForTest,
                                    String featureFile,
                                    String outputHtmlFile,
                                    String outputJsonFile,
                                    String outputrerunTxtFile,
                                    String outputsIdsDiff
                                    ) {

        script.lock("Lock_ui_test_on_${script.env.NODE_NAME}") {
            script.echo "Tests started"
            script.echo "start tests for $artifactForTest $taskKey"
            CommonUtil.safe(script) {
                script.sh "mkdir $outputsDir"
            }

            CommonUtil.shWithRuby(script, "set -x; source ~/.bashrc; bundle install")

            CommonUtil.safe(script) {
                script.sh "rm arhive.zip"
            }
            CommonUtil.safe(script) {
                script.sh "rm -rf arhive"
            }
            CommonUtil.safe(script) {
                script.sh "rm -rf ./test_servers/*"
           }
            CommonUtil.safe(script) {
                script.sh "rm -rf ./${outputsDir}/*"
            }
    
            def platform = 'android'

       
            try {
                CommonUtil.shWithRuby(script, "set -x; source ~/.bashrc; adb kill-server; adb start-server; adb devices; parallel_calabash -a ${artifactForTest} -o \"-p ${platform} -f rerun -o ${outputsDir}/${outputrerunTxtFile} -f pretty -f html -o ${outputsDir}/${outputHtmlFile}  -p json_report\" ${featuresDir}/${featureFile} --concurrent")
            }
            finally {
      
                CommonUtil.shWithRuby(script, "ruby -r \'./find_id.rb\' -e \"Find.new.get_miss_id(\'./${sourcesDir}\', \'./features/android/pages\')\"")
                script.step([$class: 'ArtifactArchiver', artifacts: outputsIdsDiff, allowEmptyArchive: true])
                CommonUtil.safe(script) {
                    script.sh "mkdir arhive"
                }
                
                script.sh "find ${outputsDir} -iname '*.json'; cp *.json ..; mv *.json ${outputJsonFile}"
                script.sh "find ${outputsDir} -iname '*.json'; cd ${outputsDir};  mv *.json ../arhive; cd ..; zip -r arhive.zip arhive "
                
            }
        }
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================
}

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

class UiTestPipelineiOSTag extends UiTestPipeline {


    //dirs
    public derivedDataPath = "${sourcesDir}"

    //environment
    public testDeviceName = "iPhone 7"
    public testOSVersion = "com.apple.CoreSimulator.SimRuntime.iOS-12-3"
    public testiOSSDK = "iphonesimulator12.3"

   public app = "Build-cal.app"
    public builtAppPattern = "${sourcesDir}/**/**.app"


    UiTestPipelineiOSTag(Object script) {
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
                stage(CHECKOUT_SOURCES, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutSourcesBody(script, sourcesDir, sourceRepoUrl, sourceBranch, sourceRepoCredentialsId)
                },
                stage(CHECKOUT_TESTS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutTestsStageBody(script, repoUrl, testBranch, testRepoCredentialsId)
                },
                stage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    buildStageBodyiOS(script,
                            sourcesDir, 
                            derivedDataPath,
                            testiOSSDK,
                            app,
                            iOSKeychainCredenialId, 
                            iOSCertfileCredentialId)
                }
        ]
        finalizeBody = { finalizeStageBody(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static buildStageBodyiOS(Object script, String sourcesDir, String derivedDataPath, String sdk, String app, String keychainCredenialId, String certfileCredentialId) {
        
        
        
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
            

            CommonUtil.shWithRuby(script, "set -x; expect -f calabash-expect.sh; set +x;")
            
            script.sh "script.sh rm -rf ${app}"
            script.sh "xcodebuild -workspace ${sourcesDir}/*.xcworkspace -scheme \"\$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//')\" -allowProvisioningUpdates -sdk ${sdk} -derivedDataPath ${derivedDataPath}"
            script.sh "mv ${sourcesDir}/Build/Products/Debug-iphonesimulator/*-cal.app/ Build-cal.app/"
            script.sh "zip -r Build-cal.app.zip Build-cal.app/"
            script.archiveArtifacts(artifacts: "Build-cal.app.zip")


        }
    }

   
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================

}

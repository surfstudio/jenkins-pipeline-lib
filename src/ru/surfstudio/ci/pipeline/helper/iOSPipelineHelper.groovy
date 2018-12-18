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
package ru.surfstudio.ci.pipeline.helper

import ru.surfstudio.ci.CommonUtil

/**
 *
 */
class iOSPipelineHelper {

    def static buildStageBodyiOS(Object script, String keychainCredenialId, String certfileCredentialId) {
        script.withCredentials([
                script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
                script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
        ]) {

            script.sh('security -v unlock-keychain -p $KEYCHAIN_PASS')
            script.sh('security import "$DEVELOPER_P12_KEY" -P "" -T /usr/bin/codesign -T /usr/bin/security')
            script.sh('security set-key-partition-list -S apple-tool:,apple: -s -k $KEYCHAIN_PASS ~/Library/Keychains/login.keychain-db')

            CommonUtil.shWithRuby(script, "gem install bundler")

            CommonUtil.shWithRuby(script, "make init")
            CommonUtil.shWithRuby(script, "make build")
        }
    }

    def static unitTestStageBodyiOS(Object script) {
        script.echo "empty"
        // TODO: Implement me
    }

    def static instrumentationTestStageBodyiOS(Object script) {
        script.echo "empty"
        // TODO: Implement me
    }

    def static staticCodeAnalysisStageBodyiOS(Object script) {
        script.sh "make sonar"
    }
}

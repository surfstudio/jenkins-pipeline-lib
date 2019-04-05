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
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.utils.android.AndroidTestUtil
import ru.surfstudio.ci.utils.android.AndroidUtil
import ru.surfstudio.ci.utils.android.config.AndroidTestConfig
import ru.surfstudio.ci.utils.android.config.AvdConfig

/**
 *
 */
class FlutterPipelineHelper {

    def static buildStageBodyAndroid(Object script, String buildShCommand) {
        script.sh buildShCommand
        script.step([$class: 'ArtifactArchiver', artifacts: '**/*.apk', allowEmptyArchive: true])
        script.step([$class: 'ArtifactArchiver', artifacts: '**/mapping.txt', allowEmptyArchive: true])
    }

    def static buildWithCredentialsStageBodyAndroid(
            Object script,
            String buildShCommand,
            String keystoreCredentials,
            String keystorePropertiesCredentials
    ) {
        script.node("$NodeProvider.flutterNode && $NodeProvider.androidNode") {
            AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials) {
                buildStageBodyAndroid(script, buildShCommand)
                script.step([$class: 'ArtifactArchiver', artifacts: '**/*.apk', allowEmptyArchive: true])
                script.step([$class: 'ArtifactArchiver', artifacts: '**/mapping.txt', allowEmptyArchive: true])
            }
        }
    }

    def static buildStageBodyIOS(Object script,
                                 String buildShCommand,
                                 String keychainCredenialId,
                                 String certfileCredentialId) {
        script.node("$NodeProvider.flutterNode && $NodeProvider.iOSNode") {
            script.withCredentials([
                    script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
                    script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
            ]) {
                script.sh('security default-keychain -s /Users/jenkins/Library/Keychains/login.keychain-db')
                script.sh('security -v unlock-keychain -p $KEYCHAIN_PASS')
                script.sh('security import "$DEVELOPER_P12_KEY" -P "" -T /usr/bin/codesign -T /usr/bin/security')
                script.sh('security set-key-partition-list -S apple-tool:,apple: -s -k $KEYCHAIN_PASS ~/Library/Keychains/login.keychain-db')
                //todo use credentials
                script.sh buildShCommand
            }
        }
    }

    def static testStageBody(
            Object script,
            String testShCommand
    ) {
        script.sh testShCommand //>> test_output.txt //todo need this? how integrate result with jenkins?
    }


    def static staticCodeAnalysisStageBody(Object script) {
        script.echo "empty"
        //todo
    }


}

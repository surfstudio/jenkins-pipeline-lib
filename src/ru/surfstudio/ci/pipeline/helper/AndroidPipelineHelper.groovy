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
import ru.surfstudio.ci.utils.android.AndroidTestConfig
import ru.surfstudio.ci.AndroidUtil

/**
 *
 */
class AndroidPipelineHelper {

    private static String DEFAULT_HTML_RESULT_FILENAME = "index.html"

    def static buildStageBodyAndroid(Object script, String buildGradleTask) {
        script.sh "./gradlew ${buildGradleTask}"
        script.step([$class: 'ArtifactArchiver', artifacts: '**/*.apk', allowEmptyArchive: true])
        script.step([$class: 'ArtifactArchiver', artifacts: '**/mapping.txt', allowEmptyArchive: true])
    }

    def static buildWithCredentialsStageBodyAndroid(
            Object script,
            String buildGradleTask,
            String keystoreCredentials,
            String keystorePropertiesCredentials
    ) {
        AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials) {
            buildStageBodyAndroid(script, buildGradleTask)
        }
    }

    def static unitTestStageBodyAndroid(
            Object script,
            String unitTestGradleTask,
            String testResultPathXml,
            String testResultPathDirHtml
    ) {
        try {
            script.sh "./gradlew ${unitTestGradleTask}"
        } finally {
            publishTestResults(script, testResultPathXml, testResultPathDirHtml, "Unit Tests")
        }
    }

    def static instrumentationTestStageBodyAndroid(
            Object script,
            AndroidTestConfig config,
            String androidTestGradleTask,
            String androidTestResultPathDirXml,
            String androidTestResultPathXml,
            String androidTestResultPathDirHtml
    ) {
        try {
            CommonUtil.gradlew(script, androidTestGradleTask)
            /**
            CommonUtil.gradlew(
                    script,
                    "clean",
                    //":connection:assembleDebugAndroidTest",
                    ":template:assembleDebug", ":template:assembleDebugAndroidTest",
                    //":network-sample:assembleDebug", ":network-sample:assembleDebugAndroidTest",
                    //":custom-view-sample:assembleDebug", ":custom-view-sample:assembleDebugAndroidTest"
            )**/
            script.sh "mkdir -p $androidTestResultPathDirXml; mkdir -p $androidTestResultPathDirHtml"
            AndroidUtil.runInstrumentalTests(script, config, androidTestResultPathDirXml)
        } finally {
            AndroidUtil.cleanup(script, config)
            publishTestResults(script, androidTestResultPathXml, androidTestResultPathDirHtml, "Instrumental tests")
        }
    }

    def static staticCodeAnalysisStageBody(Object script) {
        script.echo "empty"
        //todo
    }

    private static void publishTestResults(
            Object script,
            String testResultPathXml,
            String testResultPathDirHtml,
            String reportName
    ) {
        script.junit allowEmptyResults: true, testResults: testResultPathXml
        script.publishHTML(target: [
                allowMissing         : true,
                alwaysLinkToLastBuild: false,
                keepAll              : true,
                reportDir            : testResultPathDirHtml,
                reportFiles          : DEFAULT_HTML_RESULT_FILENAME,
                reportName           : reportName
        ])
    }
}

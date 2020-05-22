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

import ru.surfstudio.ci.utils.android.AndroidUtil

final class BackendPipelineHelper {
    private BackendPipelineHelper() {
    }

    private static String UNIT_TEST_REPORT_NAME = "Unit Tests"
    private static String DEFAULT_HTML_RESULT_FILENAME = "index.html"

    def static buildStageBodyBackend(Object script, String buildGradleTask) {
        AndroidUtil.withGradleBuildCacheCredentials(script) {
            script.sh "./gradlew ${buildGradleTask}"
            script.sh "ls build"
        }
    }

    def static runUnitTests(Object script, String testGradleTask, testResultPathXml, testResultPathDirHtml){
        try {
            AndroidUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew $testGradleTask"
            }
        } finally {
            publishTestResults(script, testResultPathXml, testResultPathDirHtml, UNIT_TEST_REPORT_NAME)
        }
    }

   def static publishTestResults(
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


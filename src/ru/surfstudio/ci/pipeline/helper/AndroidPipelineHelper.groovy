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


import ru.surfstudio.ci.utils.android.AndroidTestUtil
import ru.surfstudio.ci.utils.android.AndroidUtil
import ru.surfstudio.ci.utils.android.config.AndroidTestConfig
import ru.surfstudio.ci.utils.android.config.AvdConfig
import ru.surfstudio.ci.utils.buildsystems.GradleUtil

/**
 *
 */
class AndroidPipelineHelper {

    private static String DEFAULT_INSTRUMENTAL_TEST_REPORT_NAME = "Instrumental tests"
    private static String UNIT_TEST_REPORT_NAME = "Unit Tests"

    private static String JIRA_ISSUE_KEY_PATTERN = ~/((?<!([A-Z]{1,10})-?)[A-Z]+-\d+)/
    // https://confluence.atlassian.com/stashkb/integrating-with-custom-jira-issue-key-313460921.html?_ga=2.153274111.652352963.1580752546-1778113334.1579628389

    def static buildStageBodyAndroid(Object script, String buildGradleTask) {
        GradleUtil.withGradleBuildCacheCredentials(script) {
            script.sh "./gradlew ${buildGradleTask}"
        }
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
            GradleUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew $unitTestGradleTask"
            }
        } finally {
            script.junit allowEmptyResults: true, testResults: testResultPathXml
            AndroidTestUtil.archiveUnitTestHtmlResults(script, testResultPathDirHtml, UNIT_TEST_REPORT_NAME)
        }
    }

    @Deprecated
    def static instrumentationTestStageBodyAndroid(
            Object script,
            String testGradleTask,
            String testResultPathXml,
            String testResultPathDirHtml,
            String reportName = DEFAULT_INSTRUMENTAL_TEST_REPORT_NAME
    ) {
        ru.surfstudio.ci.AndroidUtil.onEmulator(script, "avd-main") {
            try {
                script.sh "./gradlew uninstallAll ${testGradleTask}"
            } finally {
                script.junit allowEmptyResults: true, testResults: testResultPathXml
                script.publishHTML(target: [allowMissing         : true,
                                            alwaysLinkToLastBuild: false,
                                            keepAll              : true,
                                            reportDir            : testResultPathDirHtml,
                                            reportFiles          : 'index.html',
                                            reportName           : reportName

                ])
            }
        }
    }

    def static instrumentationTestStageBodyAndroid(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            AndroidTestConfig androidTestConfig,
            String reportName = DEFAULT_INSTRUMENTAL_TEST_REPORT_NAME
    ) {
        try {
            GradleUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew ${androidTestConfig.instrumentalTestAssembleGradleTask}"
            }
            script.sh "rm -rf ${androidTestConfig.instrumentalTestResultPathDirXml}; \
                rm -rf ${androidTestConfig.instrumentalTestResultPathDirHtml}"
            script.sh "mkdir -p ${androidTestConfig.instrumentalTestResultPathDirXml}; \
                mkdir -p ${androidTestConfig.instrumentalTestResultPathDirHtml}"

            AndroidTestUtil.runInstrumentalTests(
                    script,
                    config,
                    androidTestBuildType,
                    getTestInstrumentationRunnerName,
                    androidTestConfig.instrumentalTestResultPathDirXml,
                    androidTestConfig.instrumentalTestResultPathDirHtml,
                    androidTestConfig.generateUniqueAvdNameForJob,
                    androidTestConfig.instrumentationTestRetryCount
            )
        } finally {
            AndroidTestUtil.cleanup(script, config)
            script.junit allowEmptyResults: true, testResults: "${androidTestConfig.instrumentalTestResultPathDirXml}/*.xml"
            script.publishHTML(target: [
                    allowMissing         : true,
                    alwaysLinkToLastBuild: false,
                    keepAll              : true,
                    reportDir            : androidTestConfig.instrumentalTestResultPathDirHtml,
                    reportFiles          : "*/index.html",
                    reportName           : reportName
            ])
        }
    }

    def static staticCodeAnalysisStageBody(Object script) {
        script.echo "empty"
        //todo
    }
}

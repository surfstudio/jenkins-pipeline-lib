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

import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.pr.PrPipeline
import ru.surfstudio.ci.stage.SimpleStage
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.utils.android.AndroidTestUtil
import ru.surfstudio.ci.utils.android.AndroidUtil
import ru.surfstudio.ci.utils.android.config.AndroidTestConfig
import ru.surfstudio.ci.utils.android.config.AvdConfig

/**
 *
 */
class AndroidPipelineHelper {

    private static String UNIT_TEST_REPORT_NAME = "Unit Tests"
    private static String INSTRUMENTAL_TEST_REPORT_NAME = "Instrumental tests"

    private static String DEFAULT_HTML_RESULT_FILENAME = "index.html"

    def static buildStageBodyAndroid(Object script, String buildGradleTask) {
        AndroidUtil.withGradleBuildCacheCredentials(script) {
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
            AndroidUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew $unitTestGradleTask"
            }
        } finally {
            publishTestResults(script, testResultPathXml, testResultPathDirHtml, UNIT_TEST_REPORT_NAME)
        }
    }

    @Deprecated
    def static instrumentationTestStageBodyAndroid(
            Object script,
            String testGradleTask,
            String testResultPathXml,
            String testResultPathDirHtml
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
                                            reportName           : "Instrumental Tests"

                ])
            }
        }
    }

    def static instrumentationTestStageBodyAndroid(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            AndroidTestConfig androidTestConfig
    ) {
        try {
            AndroidUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew ${androidTestConfig.instrumentalTestAssembleGradleTask}"
            }
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
            publishTestResults(
                    script,
                    "${androidTestConfig.instrumentalTestResultPathDirXml}/*.xml",
                    androidTestConfig.instrumentalTestResultPathDirHtml,
                    INSTRUMENTAL_TEST_REPORT_NAME
            )
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
                reportFiles          : "*/$DEFAULT_HTML_RESULT_FILENAME",
                reportName           : reportName
        ])
    }

    /**
     * Форматирование исходного кода на котлин
     */
    static ktlintFormatStageAndroid(
            Object script,
            String sourceBranch,
            String destinationBranch
    ) {
        def files = RepositoryUtil.filesDiffPr(script, sourceBranch, destinationBranch)
        try {
            AndroidUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew ktlintFilesFormat -PlintFiles=\"${files.join("\",\"")}\""
            }
        } catch (Exception ex) {
            script.echo "Formatting exception $ex"
        }
    }

    static boolean checkChangesAndCommit(Object script) {
        boolean hasChanges = RepositoryUtil.checkHasChanges(script)
        if (hasChanges) {
            script.sh "git commit -a -m \"Code Formatting $RepositoryUtil.SKIP_CI_LABEL1\""
        } else {
            script.echo "No modification after code formatting. "
        }
        return hasChanges
    }

    def static pushChanges(
            Object script,
            String repoUrl,
            String repoCredentialsId
    ) {
        RepositoryUtil.push(script, repoUrl, repoCredentialsId)
    }

    def static notifyAfterCodeStyleFormatting(PrPipeline ctx) {
        for (Stage stage : ctx.stages) {
            if (stage instanceof SimpleStage
                    && (stage as SimpleStage).result != Result.NOT_BUILT
                    && (stage as SimpleStage).runPreAndPostExecuteStageBodies) {
                PrPipeline.postExecuteStageBodyPr(ctx.script, stage as SimpleStage, ctx.repoUrl)
            }
        }
    }
}

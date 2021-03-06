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
package ru.surfstudio.ci.pipeline.api_test

import ru.surfstudio.ci.*
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.base.LogRotatorUtil
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.android.AndroidTestUtil
import ru.surfstudio.ci.utils.buildsystems.GradleUtil

import static ru.surfstudio.ci.CommonUtil.extractValueFromParamsAndRun

class ApiTestPipelineAndroid extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = 'Checkout'
    public static final String CHECK_API_TEST = 'Check API Test'
    public static final String WAIT_API_TEST = 'Wait API Test'

    //report names
    private static String CHECK_API_TEST_REPORT_NAME = "Check API Test"
    private static String WAIT_API_TEST_REPORT_NAME = "Wait API Test"

    //scm
    public UNDEFINED_BRANCH = "<undefined>"
    public defaultSourceBranch = UNDEFINED_BRANCH
    public sourceBranch = ""


    //tasks
    //тесты на работающие методы на сервере
    public checkApiTestGradleTask = "clean testQaUnitTest -PtestType=api"
    //тесты на апи, которые еще не работают на сервере, эти тесты должны падать при успешном прохождении теста
    public waitApiTestGradleTask = "clean testQaUnitTest -PtestType=waitApi"

    public testResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public testResultPathDirHtml = "build/reports/tests/testQaUnitTest/"

    //cron
    public cronTimeTrigger = '00 05 * * *'

    //region customization of stored artifacts

    // artifacts are only kept up to this days
    public int artifactDaysToKeep = 3
    // only this number of builds have their artifacts kept
    public int artifactNumToKeep = 10
    // history is only kept up to this days
    public int daysToKeep = 90
    // only this number of build logs are kept
    public int numToKeep = -1

    private static int ARTIFACTS_DAYS_TO_KEEP_MAX_VALUE = 5
    private static int ARTIFACTS_NUM_TO_KEEP_MAX_VALUE = 20
    private static int DAYS_TO_KEEP_MAX_VALUE = 100
    private static int NUM_TO_KEEP_MAX_VALUE = 10

    //endregion

    ApiTestPipelineAndroid(Object script) {
        super(script)
    }

    //main logic
    def init() {
        node = NodeProvider.getAndroidNode()

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT) {
                    checkoutStageBody(script, repoUrl, sourceBranch, repoCredentialsId)
                },
                stage(CHECK_API_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    test(script, checkApiTestGradleTask, testResultPathXml, testResultPathDirHtml, CHECK_API_TEST_REPORT_NAME)
                },
                stage(WAIT_API_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    test(script, waitApiTestGradleTask, testResultPathXml, testResultPathDirHtml, WAIT_API_TEST_REPORT_NAME)
                },
        ]
        finalizeBody = { finalizeStageBody(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(ApiTestPipelineAndroid ctx) {
        def script = ctx.script


        CommonUtil.printInitialStageStrategies(ctx)

        //Достаем main branch для sourceRepo, если не указали в параметрах
        extractValueFromParamsAndRun(script, SOURCE_BRANCH_PARAMETER) { value ->
            if (value != ctx.UNDEFINED_BRANCH) {
                ctx.sourceBranch = value
            }
        }
        if (!ctx.sourceBranch || ctx.sourceBranch == ctx.UNDEFINED_BRANCH) {
            ctx.sourceBranch = JarvisUtil.getMainBranch(ctx.script, ctx.repoUrl)
        }

        def buildDescription = ""
        CommonUtil.setBuildDescription(script, buildDescription)
        CommonUtil.abortDuplicateBuildsWithDescription(script, AbortDuplicateStrategy.ANOTHER, buildDescription)
    }

    def static checkoutStageBody(Object script, String url, String branch, String credentialsId) {
        script.git(
                url: url,
                credentialsId: credentialsId,
                branch: branch,
                poll: true
        )

        RepositoryUtil.saveCurrentGitCommitHash(script)
    }

    def static test(
            Object script,
            String testGradleTask,
            String testResultPathXml,
            String testResultPathDirHtml,
            String reportName
    ) {
        try {
            GradleUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew $testGradleTask"
            }
        } finally {
            script.junit allowEmptyResults: true, testResults: testResultPathXml
            AndroidTestUtil.archiveUnitTestHtmlResults(script, testResultPathDirHtml, reportName)
        }
    }

    def static finalizeStageBody(ApiTestPipelineAndroid ctx) {
        //todo выводить количество пройденных и непройденных тестов
        def link = "${CommonUtil.getBuildUrlSlackLink(ctx.script)}"
        def message
        if (ctx.jobResult == Result.FAILURE) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            message = "Ошибка прогона апи тестов из-за этапов: ${unsuccessReasons}; $link"

        } else if (ctx.jobResult == Result.UNSTABLE) {
            if (ctx.getStage(CHECK_API_TEST).result == Result.FAILURE) {
                message = "Обнаружены нерабочие методы API; $link"
            }
            if (ctx.getStage(WAIT_API_TEST).result == Result.FAILURE) {
                if (message) message += "\n"
                else message = ""
                message += "Обнаружены новые работающие методы API; $link"
            }
        }
        ctx.script.echo "Message: $message"
        if (message) {
            JarvisUtil.sendMessageToGroup(ctx.script, message, ctx.repoUrl, "bitbucket", false)
        }

    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String SOURCE_BRANCH_PARAMETER = 'sourceBranch'

    static List<Object> properties(ApiTestPipelineAndroid ctx) {
        def script = ctx.script
        return [
                buildDiscarder(ctx, script),
                parameters(script, ctx.defaultSourceBranch),
                triggers(script, ctx.cronTimeTrigger)
        ]
    }

    def static buildDiscarder(ApiTestPipelineAndroid ctx, script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.ARTIFACTS_DAYS_TO_KEEP_NAME,
                                ctx.artifactDaysToKeep,
                                ARTIFACTS_DAYS_TO_KEEP_MAX_VALUE
                        ),
                        artifactNumToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.ARTIFACTS_NUM_TO_KEEP_NAME,
                                ctx.artifactNumToKeep,
                                ARTIFACTS_NUM_TO_KEEP_MAX_VALUE
                        ),
                        daysToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.DAYS_TO_KEEP_NAME,
                                ctx.daysToKeep,
                                DAYS_TO_KEEP_MAX_VALUE
                        ),
                        numToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.NUM_TO_KEEP_NAME,
                                ctx.numToKeep,
                                NUM_TO_KEEP_MAX_VALUE
                        )
                )
        )
    }

    private static void parameters(script, String defaultSourceBranch) {
        return script.parameters([
                script.string(
                        name: SOURCE_BRANCH_PARAMETER,
                        defaultValue: defaultSourceBranch,
                        description: 'Ветка для тестирования. Необязательный параметр, если не указана, будет использоваться MainBranch repo'),
        ])
    }

    private static void triggers(script, String cronTimeTrigger) {
        return script.pipelineTriggers([
                script.cron(cronTimeTrigger),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}
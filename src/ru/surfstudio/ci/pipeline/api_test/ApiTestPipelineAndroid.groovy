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
import ru.surfstudio.ci.error.UnstableStateThrowable
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageStrategy

import ru.surfstudio.ci.utils.android.AndroidUtil

import java.util.regex.Pattern

import static ru.surfstudio.ci.AndroidUtil.*

class ApiTestPipelineAndroid extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = 'Checkout'
    public static final String API_TEST = 'API Test'
    public static final String WAITING_API_TEST = 'Waiting API Test'

    //report names
    private static String API_TEST_REPORT_NAME = "API Test"
    private static String WAITING_API_TEST_REPORT_NAME = "Waiting API Test"

    //scm
    public UNDEFINED_BRANCH = "<undefined>"
    public defaultSourceBranch = UNDEFINED_BRANCH
    public sourceBranch = ""


    //tasks //todo заменить на новый механизм через ApiTestRunner
    public apiTestGradleTask = "clean testQaUnitTest --tests *.*TestApi.test*" //тесты на работающие методы на сервере
    public waitingApiTestGradleTask = "clean testQaUnitTest --tests *.*TestApi.wait*" //тесты на апи, которые еще не работают на сервере, эти тесты должны падать при успешном прохождении теста

    public testResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public testResultPathDirHtml = "app-injector/build/reports/tests/testQaUnitTest/"

    //cron
    public cronTimeTrigger = '00 05 * * *'

    ApiTestPipelineAndroid(Object script) {
        super(script)
    }

    //main logic
    @Override
    def init() {
        node = NodeProvider.getAndroidNode()

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                createStage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutStageBody(script, repoUrl, sourceBranch, repoCredentialsId)
                },
                createStage(API_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    test(script, apiTestGradleTask, testResultPathXml, testResultPathDirHtml, API_TEST_REPORT_NAME)
                },
                createStage(WAITING_API_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    test(script, waitingApiTestGradleTask, testResultPathXml, testResultPathDirHtml, WAITING_API_TEST_REPORT_NAME)
                },
        ]
        finalizeBody = { finalizeStageBody(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(ApiTestPipelineAndroid ctx) {
        def script = ctx.script


        CommonUtil.printInitialStageStrategies(ctx)

        //Достаем main branch для sourceRepo, если не указали в параметрах
        if (!ctx.sourceBranch || ctx.sourceBranch == ctx.UNDEFINED_BRANCH) {
            ctx.sourceBranch = JarvisUtil.getMainBranch(ctx.script, ctx.repoUrl)
        }

        def buildDescription = ""
        CommonUtil.setBuildDescription(script, buildDescription)
        CommonUtil.abortDuplicateBuildsWithDescription(script, AbortDuplicateStrategy.ANOTHER, buildDescription)
    }

    def static checkoutStageBody(Object script,  String url, String branch, String credentialsId) {
        script.git(
                url: url,
                credentialsId: credentialsId,
                branch: branch,
                poll: true
        )

        RepositoryUtil.checkLastCommitMessageContainsSkipCiLabel(script)

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
            AndroidUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew $testGradleTask"
            }
        } finally {
            script.junit allowEmptyResults: true, testResults: testResultPathXml
            script.publishHTML(target: [
                    allowMissing         : true,
                    alwaysLinkToLastBuild: false,
                    keepAll              : true,
                    reportDir            : testResultPathDirHtml,
                    reportFiles          : "*/index.html",
                    reportName           : reportName
            ])
        }
    }

    def static finalizeStageBody(ApiTestPipelineAndroid ctx) { //todo выводить количество пройденных и непройденных тестов
        def link = "${CommonUtil.getBuildUrlMarkdownLink(ctx.script)}"
        def message
        if (ctx.jobResult == Result.FAILURE) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            message = "Ошибка прогона апи тестов из-за этапов: ${unsuccessReasons}; $link"

        } else if(ctx.jobResult == Result.UNSTABLE) {
            if(ctx.getStage(API_TEST).result = Result.UNSTABLE) {
                message = "Обнаружены нерабочие методы API; $link"
            }
            if(ctx.getStage(WAITING_API_TEST).result = Result.UNSTABLE) {
                if(message) message+= "\n"
                else message = ""
                message += "Обнаружены новые работающие методы API; $link"
            }
        }
        if (message) {
            JarvisUtil.sendMessageToGroup(ctx.script, message, ctx.repoUrl, "bitbucket", false)
        }

    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String SOURCE_BRANCH_PARAMETER = 'sourceBranch'


    def static List<Object> properties(ApiTestPipelineAndroid ctx) {
        def script = ctx.script
        return [
                buildDiscarder(script),
                parameters(script, ctx.defaultSourceBranch, ctx.node),
                triggers(script, ctx.cronTimeTrigger)
        ]
    }

    def static buildDiscarder(script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: '3',
                        artifactNumToKeepStr: '10',
                        daysToKeepStr: '90',
                        numToKeepStr: '')
        )
    }

    private static void parameters(script, String defaultSourceBranch, String node) {
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
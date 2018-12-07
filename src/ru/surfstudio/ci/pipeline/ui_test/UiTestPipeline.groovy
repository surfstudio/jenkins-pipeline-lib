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

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.extractValueFromEnvOrParamsAndRun
import static ru.surfstudio.ci.CommonUtil.extractValueFromParamsAndRun

abstract class UiTestPipeline extends ScmPipeline {

    //stage names
    public static final String CHECKOUT_SOURCES = 'Checkout Sources'
    public static final String CHECKOUT_TESTS = 'Checkout Tests'
    public static final String BUILD = 'Build'
    public static final String PREPARE_ARTIFACT = 'Prepare Artifact'
    public static final String PREPARE_TESTS = 'Prepare Tests'
    public static final String TEST = 'Test'
    public static final String PUBLISH_RESULTS = 'Publish Results'
    public static final String TASK_NAME_PARAMETER = 'taskName'

    //required initial configuration
    public sourceRepoUrl // repo with app sources
    public jiraProjectKey
    public platform  // "android" or "ios"
    public testBranch // branch with tests
    public defaultTaskKey  //task for run periodically

    //dirs
    public sourcesDir = "sources"
    public featuresDir = "features"
    public outputsDir = "outputs"

    //files
    public featureForTest = "for_test.feature"
    public outputJsonFile = "report.json"
    public outputHtmlFile = "report.html"
    public outputrerunTxtFile = "rerun.txt"

    //credentials
    public jiraAuthenticationName = 'Jarvis_Jira'

    //scm
    public UNDEFINED_BRANCH = "<undefined>"
    public defaultSourceBranch = UNDEFINED_BRANCH
    public sourceBranch = ""

    //jira
    public taskKey = ""
    public taskName = ""
    public userEmail = ""

    //notification
    public notificationEnabled = true

    //ios
    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    UiTestPipeline(Object script) {
        super(script)
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(UiTestPipeline ctx) {
        def script = ctx.script
        
        CommonUtil.checkPipelineParameterDefined(script, ctx.sourceRepoUrl, "sourceRepoUrl")
        CommonUtil.checkPipelineParameterDefined(script, ctx.jiraProjectKey, "jiraProjectKey")
        CommonUtil.checkPipelineParameterDefined(script, ctx.platform, "platform")
        CommonUtil.checkPipelineParameterDefined(script, ctx.testBranch, "testBranch")
        CommonUtil.checkPipelineParameterDefined(script, ctx.defaultTaskKey, "defaultTaskKey")

        CommonUtil.printInitialStageStrategies(ctx)

        //если триггером был webhook параметры устанавливаются как env, если запустили вручную, то устанавливается как params
        extractValueFromParamsAndRun(script, NODE_PARAMETER) { value ->
            ctx.node = value
            script.echo "Using node from params: ${ctx.node}"
        }

        //scm
        extractValueFromParamsAndRun(script, SOURCE_BRANCH_PARAMETER) {
            value -> ctx.sourceBranch = value
        }
        extractValueFromParamsAndRun(script, TEST_BRANCH_PARAMETER) {
            value -> ctx.testBranch = value
        }

        //jira
        extractValueFromEnvOrParamsAndRun(script, TASK_KEY_PARAMETER) {
            value -> ctx.taskKey = value
        }
        extractValueFromEnvOrParamsAndRun(script, TASK_NAME_PARAMETER) {
            value -> ctx.taskName = value
        }
        extractValueFromEnvOrParamsAndRun(script, USER_EMAIL_PARAMETER) {
            value -> ctx.userEmail = value
        }

        if(ctx.notificationEnabled) {
            sendStartNotification(ctx)
        }

        //Достаем main branch для sourceRepo, если не указали в параметрах
        if (!ctx.sourceBranch || ctx.sourceBranch == UNDEFINED_BRANCH) {
            ctx.sourceBranch = JarvisUtil.getMainBranch(ctx.script, ctx.sourceRepoUrl)
        }

        def buildDescription = "taskKey: ${ctx.taskKey}, testBranch: ${ctx.testBranch}, sourceBranch: ${ctx.sourceBranch}"
        CommonUtil.setBuildDescription(script, buildDescription)
        CommonUtil.abortDuplicateBuildsWithDescription(script, AbortDuplicateStrategy.ANOTHER, buildDescription)

        checkAndParallelBulkJob(ctx)
    }

    def static checkoutSourcesBody(Object script, String sourcesDir, String sourceRepoUrl, String sourceBranch, String credentialsId) {
        script.dir(sourcesDir) {
            CommonUtil.safe(script) {
                script.sh "rm -rf ./*"
            }
            script.git(
                    url: sourceRepoUrl,
                    credentialsId: credentialsId,
                    branch: sourceBranch
            )
        }
    }

    def static checkoutTestsStageBody(Object script, String testsRepoUrl, String testsBranch, String credentialsId) {
        script.git(
                url: testsRepoUrl,
                credentialsId: credentialsId,
                branch: testsBranch
        )
    }

    /**
     * скачивает .feature для taskKey из Xray Jira и сохраняет в файл newFeatureForTest в папку featuresDir
     */
    def static prepareTestsStageBody(Object script,
                                     String jiraAuthenticationName,
                                     String taskKey,
                                     String featuresDir,
                                     String newFeatureForTest) {
        def response = script.httpRequest consoleLogResponseBody: true,
                url: "${Constants.JIRA_URL}rest/raven/1.0/export/test?keys=${taskKey}",
                authentication: jiraAuthenticationName
        httpMode: 'GET'
        CommonUtil.safe(script) {
            script.sh "rm ${newFeatureForTest}"
        }
        script.dir(featuresDir) {
            script.writeFile file: newFeatureForTest, text: response.content
        }
    }

    def static publishResultsStageBody(Object script,
                                       String outputsDir,
                                       String outputJsonFile,
                                       String outputHtmlFile,
                                       String outputrerunTxtFile,
                                       String jiraAuthenticationName,
                                       String htmlReportName) {
        script.dir(outputsDir) {
            //def testResult = script.readFile file: outputJsonFile
            //script.echo "Test result json: $testResult"
            script.withCredentials([script.usernamePassword(
                    credentialsId: jiraAuthenticationName,
                    usernameVariable: 'USERNAME',
                    passwordVariable: 'PASSWORD')]) {

                script.echo "publish result bot username=${script.env.USERNAME}"
                //http request plugin не пашет, видимо что то с форматом body

                //script.sh "curl -H \"Content-Type: application/json\" -X POST -u ${script.env.USERNAME}:${script.env.PASSWORD} --data @arhive.zip ${Constants.JIRA_URL}rest/raven/1.0/import/execution/cucumber"
                script.sh "cd .. && ls"
                script.sh "cd .. && curl -H \"Content-Type: multipart/form-data\" -u ${script.env.USERNAME}:${script.env.PASSWORD} -F \"file=@arhive.zip\" ${Constants.JIRA_URL}rest/raven/1.0/import/execution/bundle"
            }
            script.step([$class: 'ArtifactArchiver', artifacts: outputrerunTxtFile, allowEmptyArchive: true]) 
            CommonUtil.safe(script){

                script.sh "rm arhive.zip"
            }


        }
        script.publishHTML(target: [allowMissing         : true,
                                    alwaysLinkToLastBuild: false,
                                    keepAll              : true,
                                    reportDir            : outputsDir,
                                    reportFiles          : outputHtmlFile,
                                    reportName           : htmlReportName
        ])

    }

    def static finalizeStageBody(UiTestPipeline ctx) {
        if(!isBulkJob(ctx)) {
            def script = ctx.script
            if (ctx.notificationEnabled) {
                sendFinishNotification(ctx)
            }
            def newTaskStatus = ctx.jobResult == Result.SUCCESS ? "DONE" : "BLOCKED"
            JarvisUtil.changeTaskStatus(script, newTaskStatus, ctx.taskKey)
        }
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // =============================================+++++ ↓↓↓ EXECUTION UTILS ↓↓↓  ===================================================


    def static sendStartNotification(UiTestPipeline ctx) {
        def jenkinsLink = CommonUtil.getBuildUrlMarkdownLink(ctx.script)
        if(isBulkJob(ctx)){
            sendMessage(ctx,"Запущено параллельное выполнение тестов прогонов ${ctx.taskKey}. ${jenkinsLink}", true)
        } else {
            def testExecutionLink = CommonUtil.getJiraTaskMarkdownLink(ctx.taskKey)
            def testExecutionName = ctx.taskName ? "\"${ctx.taskName}\"" : ""
            sendMessage(ctx, "Запущено выполнение тестов прогона ${testExecutionLink} ${testExecutionName}. ${jenkinsLink}", true)
        }
    }

    def static sendFinishNotification(UiTestPipeline ctx) {
        def testExecutionLink = CommonUtil.getJiraTaskMarkdownLink(ctx.taskKey)
        def jenkinsLink = CommonUtil.getBuildUrlMarkdownLink(ctx.script)
        def testExecutionName = ctx.taskName ? "\"${ctx.taskName}\"" : ""
        if (ctx.jobResult != Result.SUCCESS) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Tесты прогона ${testExecutionLink} ${testExecutionName} не выполнены из-за этапов: ${unsuccessReasons}. ${jenkinsLink}"
            sendMessage(ctx, message, false)
        } else {
            sendMessage(ctx, "Tесты прогона ${testExecutionLink} ${testExecutionName} выполнены", true)
        }
    }

    def static sendMessage(UiTestPipeline ctx, String message, boolean success) {
        if (ctx.userEmail) {
            JarvisUtil.sendMessageToUser(ctx.script, message, ctx.userEmail, "hipchat")
        } else {
            JarvisUtil.sendMessageToGroup(ctx.script, message, ctx.sourceRepoUrl, "bitbucket", success)
        }
    }

    def static Boolean checkAndParallelBulkJob(UiTestPipeline ctx) {
        if (isBulkJob(ctx)) {
            def script = ctx.script
            script.echo "Parallel bulk UI TEST job"
            def tasks = ctx.taskKey.split(",")
            for (task in tasks) {
                CommonUtil.safe(script) {
                    CommonUtil.startCurrentBuildCloneWithParams(
                            script,
                            [
                                script.string(name: TASK_KEY_PARAMETER, value: task.trim())
                            ]
                    )
                }
            }
            for (stage in ctx.stages) {
                stage.strategy = StageStrategy.SKIP_STAGE
            }
            ctx.jobResult = Result.NOT_BUILT
            return true
        } else {
            return false
        }
    }

    def static isBulkJob(UiTestPipeline ctx){
        return ctx.taskKey.contains(",")
    }
    // =============================================== 	↑↑↑  END EXECUTION UTILS ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String TASK_KEY_PARAMETER = 'taskKey'
    public static final String TEST_BRANCH_PARAMETER = 'testBranch'
    public static final String SOURCE_BRANCH_PARAMETER = 'sourceBranch'
    public static final String USER_EMAIL_PARAMETER = 'userEmail'
    public static final String NODE_PARAMETER = 'node'

    def static List<Object> properties(UiTestPipeline ctx) {
        def script = ctx.script
        return [
                buildDiscarder(script),
                environments(script, ctx.testBranch),
                parameters(script, ctx.defaultTaskKey, ctx.testBranch, ctx.defaultSourceBranch, ctx.node),
                triggers(script, ctx.jiraProjectKey, ctx.platform)
        ]
    }

    def static buildDiscarder(script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: '3',
                        artifactNumToKeepStr: '10',
                        daysToKeepStr: '180',
                        numToKeepStr: '')
        )
    }

    /**
     * @param testBranch - ветка в которой находятся тесты
     */
    def static environments(script, String testBranch) {
        return  [
                $class: 'EnvInjectJobProperty',
                info: [
                        loadFilesFromMaster: false,
                        propertiesContent: "testBranch=$testBranch",  //используется в том числе для получения jenkinsFile из репозитория с тестами
                        secureGroovyScript: [classpath: [], sandbox: false, script: '']],
                keepBuildVariables: true,
                keepJenkinsSystemVariables: true,
                on: true
        ]

    }

    private static void parameters(script, String defaultTaskKey, String testBranch, String defaultSourceBranch, String node) {
        return script.parameters([
                script.string(
                        name: TASK_KEY_PARAMETER,
                        defaultValue: defaultTaskKey,
                        description: 'Task типа "Test Execution" для которого необходимо запустить тесты. Обязательный параметр. Значение по умолчанию будет использоваться для запуска тестов по таймеру'),
                script.string(
                        name: TEST_BRANCH_PARAMETER,
                        defaultValue: testBranch,
                        description: 'Ветка в репозитории с тестами, обязательный параметр'),
                script.string(
                        name: SOURCE_BRANCH_PARAMETER,
                        defaultValue: defaultSourceBranch,
                        description: 'Ветка, с исходным кодом приложения, из которой нужно собрать сборку. Необязательный параметр, если не указана, будет использоваться MainBranch repo '),
                script.string(
                        name: USER_EMAIL_PARAMETER,
                        description: 'почта пользователя, которому будут отсылаться уведомления о результатах тестирования. Если не указано. то сообщения будут отсылаться в группу проекта'),
                script.string(
                        name: NODE_PARAMETER,
                        defaultValue: node,
                        description: 'Node на котором будет выполняться job'),

        ])
    }

    /**
     * @param script
     * @param jiraProjectKey
     * @param platform "android" or "ios"
     */
    private static void triggers(script, String jiraProjectKey, String platform) {
        return script.pipelineTriggers([
                script.cron('00 09 * * *'),
                script.GenericTrigger(
                        genericVariables: [
                                [
                                        key  : TASK_KEY_PARAMETER,
                                        value: '$.issue.key'
                                ],
                                [
                                        key  : 'labelsWh',
                                        value: '$.issue.fields.labels'
                                ],
                                [
                                        key  : USER_EMAIL_PARAMETER,
                                        value: '$.user.emailAddress'
                                ],
                                [
                                        key  : 'jiraProjectKeyWh',
                                        value: '$.issue.fields.project.key'
                                ],
                                [
                                        key  : TASK_NAME_PARAMETER,
                                        value: '$.issue.fields.summary'
                                ],
                                [
                                        key  : 'testEnvironments',
                                        value: '$.issue.fields.customfield_10225'
                                ],

                        ],
                        printContributedVariables: true,
                        printPostContent: true,
                        causeString: 'Triggered by Bitbucket',
                        regexpFilterExpression: /^$jiraProjectKey .*$platform.* .*Auto.*$/,
                        regexpFilterText: '$jiraProjectKeyWh $labelsWh $testEnvironments'
                ),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}

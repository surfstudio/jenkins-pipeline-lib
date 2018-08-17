package ru.surfstudio.ci.pipeline.ui_test

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.base.AutoAbortedPipeline
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

abstract class UiTestPipeline extends AutoAbortedPipeline {

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

    //credentials
    public jiraAuthenticationName = 'Jarvis_Jira'

    //scm
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

    @Override
    String getBuildIdentifier() {
        return "taskKey: ${taskKey}, testBranch: ${testBranch}, sourceBranch: ${sourceBranch}"
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initStageBody(UiTestPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)

        //Выбираем значения веток, прогона и тд из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную

        //scm
        applyParameterIfNotEmpty(script, SOURCE_BRANCH_PARAMETER, script.params[SOURCE_BRANCH_PARAMETER], {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(script, TEST_BRANCH_PARAMETER, script.env.testBranch, {
            value -> ctx.testBranch = value
        })
        applyParameterIfNotEmpty(script, TEST_BRANCH_PARAMETER, script.params[TEST_BRANCH_PARAMETER], {
            value -> ctx.testBranch = value
        })

        //jira
        applyParameterIfNotEmpty(script, TASK_KEY_PARAMETER, script.params[TASK_KEY_PARAMETER], {
            value -> ctx.taskKey = value
        })
        applyParameterIfNotEmpty(script, TASK_NAME_PARAMETER, script.params[TASK_NAME_PARAMETER], {
            value -> ctx.taskName = value
        })
        applyParameterIfNotEmpty(script, USER_EMAIL_PARAMETER, script.params[USER_EMAIL_PARAMETER], {
            value -> ctx.userEmail = value
        })

        if(ctx.notificationEnabled) {
            sendStartNotification(ctx)
        }

        //Достаем main branch для sourceRepo, если не указали в параметрах
        if (!ctx.sourceBranch) {
            ctx.sourceBranch = JarvisUtil.getMainBranch(ctx.script, ctx.sourceRepoUrl)
        }

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

    def static checkAndParallelBulkJob(UiTestPipeline ctx) {
        if(isBulkJob(ctx)) {
            def script = ctx.script
            script.echo "Parallel bulk UI TEST job"
            def tasks = ctx.taskKey.split(",")
            for (task in tasks) {
                CommonUtil.safe(script) {
                    script.build job: script.env.JOB_NAME, parameters: [
                            script.string(name: TASK_KEY_PARAMETER, value: task.trim()),
                            script.string(name: TEST_BRANCH_PARAMETER, value: ctx.testBranch),
                            script.string(name: SOURCE_BRANCH_PARAMETER, value: ctx.sourceBranch),
                            script.string(name: USER_EMAIL_PARAMETER, value: ctx.userEmail)
                    ]
                }
            }
            for (stage in ctx.stages) {
                if (stage.name != ctx.INIT) {
                    stage.strategy = StageStrategy.SKIP_STAGE
                }
            }
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
        checkConfiguration(ctx)
        return [
                buildDiscarder(script),
                environments(script, ctx.testBranch),
                parameters(script, ctx.defaultTaskKey, ctx.testBranch, ctx.node),
                triggers(script, ctx.jiraProjectKey, ctx.platform)
        ]
    }

    def static checkConfiguration(UiTestPipeline ctx) {
        def script = ctx.script

        CommonUtil.checkConfigurationParameterDefined(script, ctx.sourceRepoUrl, "sourceRepoUrl")
        CommonUtil.checkConfigurationParameterDefined(script, ctx.jiraProjectKey, "jiraProjectKey")
        CommonUtil.checkConfigurationParameterDefined(script, ctx.platform, "platform")
        CommonUtil.checkConfigurationParameterDefined(script, ctx.testBranch, "testBranch")
        CommonUtil.checkConfigurationParameterDefined(script, ctx.defaultTaskKey, "defaultTaskKey")
        CommonUtil.checkConfigurationParameterDefined(script, ctx.node, "node")
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

    private static void parameters(script, String defaultTaskKey, String testBranch, String node) {
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
                        description: 'Ветка, с исходным кодом приложения, из которой нужно собрать сборку. Необязательный параметр, если не указана, будет использоваться MainBranch repo '),
                script.string(
                        name: USER_EMAIL_PARAMETER,
                        description: 'почта пользователя, которому будут отсылаться уведомления о результатах тестирования. Если не указано. то сообщения будут отсылаться в группу проекта'),
                script.string(
                        name: NODE_PARAMETER,
                        defaultValue: node,
                        description: 'Node на котором будет выполняться job'),
                script.string(
                        name: TASK_NAME_PARAMETER,
                        description: 'Необязательный параметр, присутствует здесь для правильного разбора json из webhook'),


        ])
    }

    /**
     * @param script
     * @param jiraProjectKey
     * @param platform "android" or "ios"
     */
    private static void triggers(script, String jiraProjectKey, String platform) {
        return script.pipelineTriggers([
                script.cron('0 9 * * *'),
                script.GenericTrigger(
                        genericVariables: [
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

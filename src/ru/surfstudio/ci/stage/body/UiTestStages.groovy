package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.AndroidUtil
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.UiTestPipeline
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

class UiTestStages {

    def static initStageBody(UiTestPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)

        //Выбираем значения веток, прогона и тд из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную

        //scm
        applyParameterIfNotEmpty(script, 'sourceBranch', script.params.sourceBranch, {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(script, 'sourceRepoUrl', script.env.SOURCE_REPO_URL, {
            value -> ctx.sourceRepoUrl = value
        })
        applyParameterIfNotEmpty(script, 'testBranch', script.env.DEFAULT_TEST_BRANCH, {
            value -> ctx.testBranch = value
        })
        applyParameterIfNotEmpty(script, 'testBranch', script.params.testBranch, {
            value -> ctx.testBranch = value
        })

        //jira
        applyParameterIfNotEmpty(script, 'taskKey', script.params.taskKey, {
            value -> ctx.taskKey = value
        })
        applyParameterIfNotEmpty(script, 'taskName', script.params.taskName, {
            value -> ctx.taskName = value
        })
        applyParameterIfNotEmpty(script, 'userEmail', script.params.userEmail, {
            value -> ctx.userEmail = value
        })

        if(ctx.notificationEnabled) {
            sendStartNotification(ctx)
        }

        //Достаем main branch для sourceRepo, если не указали в параметрах
        if (!ctx.sourceBranch) {
            ctx.sourceBranch = JarvisUtil.getMainBranch(ctx.script, ctx.sourceRepoUrl)
        }

        CommonUtil.abortDuplicateBuilds(script, "taskKey: ${ctx.taskKey}, testBranch: ${ctx.testBranch}, sourceBranch: ${ctx.sourceBranch}")

        checkAndParallelBulkJob(ctx)
    }

    def static checkoutSourcesBody(Object script, String sourcesDir, String sourceRepoUrl, String sourceBranch) {
        def credentialsId = script.scm.userRemoteConfigs.first().credentialsId
        script.echo("Using credentials ${credentialsId} for checkout")
        script.dir(sourcesDir) {
            script.checkout([
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${sourceBranch}"]],
                    doGenerateSubmoduleConfigurations: script.scm.doGenerateSubmoduleConfigurations,
                    userRemoteConfigs                : [[credentialsId: credentialsId, url:sourceRepoUrl]],
            ])
        }
    }

    def static checkoutTestsStageBody(Object script, String testBranch) {
        script.checkout([
                $class                           : 'GitSCM',
                branches                         : [[name: "${testBranch}"]],
                doGenerateSubmoduleConfigurations: script.scm.doGenerateSubmoduleConfigurations,
                userRemoteConfigs                : script.scm.userRemoteConfigs,
        ])
    }

    def static buildStageBodyAndroid(Object script, String sourcesDir, String buildGradleTask) {
        script.dir(sourcesDir) {
            script.sh "./gradlew ${buildGradleTask}"
        }
    }

    def static buildStageBodyiOS(Object script, String sourcesDir, String derivedDataPath, String sdk, String keychainCredenialId, String certfileCredentialId) {            
        script.withCredentials([
            script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
            script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
        ]) {
            script.sh 'security -v unlock-keychain -p $KEYCHAIN_PASS'
            script.sh 'security import "$DEVELOPER_P12_KEY" -P ""'
            
            //script.sh "gem install bundler"

            script.dir(sourcesDir) {
                script.sh "make init" 
            }   

            script.sh "bundle install"
            script.sh "echo -ne 'yes \n' | bundle exec calabash-ios setup ${sourcesDir}"
            
            script.sh "xcodebuild -workspace ${sourcesDir}/*.xcworkspace -scheme \$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//') -allowProvisioningUpdates -sdk ${sdk} -derivedDataPath ${derivedDataPath}"
        }
    }

    def static prepareApkStageBodyAndroid(Object script, String builtApkPattern, String newApkForTest) {
        //script.step([$class: 'ArtifactArchiver', artifacts: builtApkPattern])

        //def files = script.findFiles(glob: builtApkPattern)
        //String foundedApks = files.join("\n")
        //script.echo "founded apks: $foundedApks"
        //def apkPath = files[0].path
        //script.echo "use first: $apkPath"

        //script.sh "mv \"${apkPath}\" ${newApkForTest}"
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
        script.dir(featuresDir) {
            script.writeFile file: newFeatureForTest, text: response.content
        }
    }

    def static testStageBodyiOS(Object script,
                             String taskKey,
                             String sourcesDir,
                             String derivedDataPath,
                             String device,
                             String iosVersion,
                             String outputsDir,
                             String featuresDir,
                             String featureFile,
                             String outputHtmlFile,
                             String outputJsonFile) {
            
            def simulatorIdentifierFile = "currentsim"

            script.echo "Setting up simulator ..."
            script.sh "xcrun simctl create \"MyTestiPhone\" \"${device}\" \"${iosVersion}\" > ${simulatorIdentifierFile}"  
            script.sh "xcrun simctl create \"MyTestiPhone\" \"${device}\" \"${iosVersion}\" > ${simulatorIdentifierFile}"   
            script.sh "xcrun simctl list"
            script.sh "xcrun simctl shutdown all"     
      
            script.sh "xcrun simctl boot \$(cat ${simulatorIdentifierFile})"
            script.sh "xcrun simctl install booted ${derivedDataPath}/Build/Products/Debug-iphonesimulator/*.app"
            
            script.echo "Tests started"
            script.echo "start tests for $taskKey"
            CommonUtil.safe(script) {
                script.sh "mkdir $outputsDir"
            }
            
            try {
                script.echo "${derivedDataPath}"
                script.echo "${derivedDataPath}/Build/Products/Debug-iphonesimulator/\$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//').app"
                script.sh "xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\-cal' | sed 's/ *//'"    
                script.sh "APP_BUNDLE_PATH=${derivedDataPath}/Build/Products/Debug-iphonesimulator/\$(xcodebuild -workspace ${sourcesDir}/*.xcworkspace -list | grep '\\-cal' | sed 's/ *//').app DEVICE_TARGET=\$(cat ${simulatorIdentifierFile}) bundle exec cucumber -p ios ${featuresDir}/${featureFile} -f html -o ${outputsDir}/${outputHtmlFile} -f json -o ${outputsDir}/${outputJsonFile} -f pretty"
            } finally {
                script.echo "Removing simulator ..."
                script.sh "xcrun simctl delete \$(cat ${simulatorIdentifierFile})"
            }
    }

    def static publishResultsStageBody(Object script,
                                       String outputsDir,
                                       String outputJsonFile,
                                       String outputHtmlFile,
                                       String jiraAuthenticationName,
                                       String htmlReportName) {
        script.dir(outputsDir) {
            def testResult = script.readFile file: outputJsonFile
            script.echo "Test result json: $testResult"
            script.withCredentials([script.usernamePassword(
                    credentialsId: jiraAuthenticationName,
                    usernameVariable: 'USERNAME',
                    passwordVariable: 'PASSWORD')]) {

                script.echo "publish result bot username=${script.env.USERNAME}"
                //http request plugin не пашет, видимо что то с форматом body
                script.sh "curl -H \"Content-Type: application/json\" -X POST -u ${script.env.USERNAME}:${script.env.PASSWORD} --data @${outputJsonFile} ${Constants.JIRA_URL}rest/raven/1.0/import/execution/cucumber"
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
        if(!ifBulkJob(ctx)) {
            def script = ctx.script
            if (ctx.notificationEnabled) {
                sendFinishNotification(ctx)
            }
            def newTaskStatus = ctx.jobResult == Result.SUCCESS ? "DONE" : "BLOCKED"
            JarvisUtil.changeTaskStatus(script, newTaskStatus, ctx.taskKey)
        }
    }

    // ================================== UTILS ===================================

    def static sendStartNotification(UiTestPipeline ctx) {
        def jenkinsLink = CommonUtil.getBuildUrlHtmlLink(ctx.script)
        if(ifBulkJob(ctx)){
            sendMessage(ctx,"Запущено параллельное выполнение тестов прогонов ${ctx.taskKey}. ${jenkinsLink}", true)
        } else {
            def testExecutionLink = CommonUtil.getJiraTaskHtmlLink(ctx.taskKey)
            def testExecutionName = ctx.taskName ? "\"${ctx.taskName}\"" : ""
            sendMessage(ctx, "Запущено выполнение тестов прогона ${testExecutionLink} ${testExecutionName}. ${jenkinsLink}", true)
        }
    }

    def static sendFinishNotification(UiTestPipeline ctx) {
        def testExecutionLink = CommonUtil.getJiraTaskHtmlLink(ctx.taskKey)
        def jenkinsLink = CommonUtil.getBuildUrlHtmlLink(ctx.script)
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
        if(ifBulkJob(ctx)) {
            def script = ctx.script
            script.echo "Parallel bulk UI TEST job"
            def tasks = ctx.taskKey.split(",")
            for (task in tasks) {
                CommonUtil.safe(script) {
                    script.build job: script.env.JOB_NAME, parameters: [
                            script.string(name: 'taskKey', value: task.trim()),
                            script.string(name: 'testBranch', value: ctx.testBranch),
                            script.string(name: 'sourceBranch', value: ctx.sourceBranch),
                            script.string(name: 'userEmail', value: ctx.userEmail)
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

    def static ifBulkJob(UiTestPipeline ctx){
        return ctx.taskKey.contains(",")
    }

}
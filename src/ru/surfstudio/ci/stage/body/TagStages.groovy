package ru.surfstudio.ci.stage.body
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.PrPipeline
import ru.surfstudio.ci.pipeline.TagPipeline

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty
import static ru.surfstudio.ci.CommonUtil.printDefaultVar

class TagStages {

    def static tagInitStageBody(TagPipeline ctx) {
        def script = ctx.script
        printDefaultVar(script, 'checkoutStageStrategy', ctx.checkoutStageStrategy)
        printDefaultVar(script,'buildStageStrategy', ctx.buildStageStrategy)
        printDefaultVar(script,'unitTestStageStrategy', ctx.unitTestStageStrategy)
        printDefaultVar(script,'smallInstrumentationTestStageStrategy', ctx.smallInstrumentationTestStageStrategy)
        printDefaultVar(script,'staticCodeAnalysisStageStrategy', ctx.staticCodeAnalysisStageStrategy)
        printDefaultVar(script,'betaUploadStageStrategy', ctx.betaUploadStageStrategy)


        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        //Параметры могут быть установлены только если Job стартовали вручную
        applyParameterIfNotEmpty(script,'checkoutStageStrategy', script.params.checkoutStageStrategy) {
            param -> ctx.checkoutStageStrategy = param
        }
        applyParameterIfNotEmpty(script,'buildStageStrategy', script.params.buildStageStrategy) {
            param -> ctx.buildStageStrategy = param
        }
        applyParameterIfNotEmpty(script,'unitTestStageStrategy', script.params.unitTestStageStrategy) {
            param -> ctx.unitTestStageStrategy = param
        }
        applyParameterIfNotEmpty(script,'smallInstrumentationTestStageStrategy', script.params.smallInstrumentationTestStageStrategy) {
            param -> ctx.smallInstrumentationTestStageStrategy = param
        }
        applyParameterIfNotEmpty(script,'staticCodeAnalysisStageStrategy', script.params.staticCodeAnalysisStageStrategy) {
            param -> ctx.staticCodeAnalysisStageStrategy = param
        }
        applyParameterIfNotEmpty(script,'betaUploadStageStrategy', script.params.betaUploadStageStrategy) {
            param -> ctx.betaUploadStageStrategy = param
        }

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        //Используется имя repoTag_0 из за особенностей jsonPath в GenericWebhook plugin
        applyParameterIfNotEmpty(script,'repoTag', script.params.repoTag_0, {
            value -> repoTag = value
        })

        CommonUtil.abortDuplicateBuilds(script, ctx.repoTag)
    }

    def static tagCheckoutStageBody(Object script, String repoTag) {
        script.checkout([
                $class                           : 'GitSCM',
                branches                         : [[name: "refs/tags/${repoTag}"]],
                doGenerateSubmoduleConfigurations: script.scm.doGenerateSubmoduleConfigurations,
                userRemoteConfigs                : script.scm.userRemoteConfigs,
        ])
        script.echo 'Checkout Success'

    }

    def static betaUploadStageAndroidBody(Object script, String betaUploadGradleTask) {
        script.sh "./gradlew ${betaUploadGradleTask}"
    }

    def static tagFinalizeStageBody(TagPipeline ctx) {
        def script = ctx.script
        def stageResultsBody = []
        for (stage in ctx.stages) {
            stageResultsBody.add([name: stage.name, status: stage.result])
        }

        def body = [
                build   : [
                        job_name     : script.env.JOB_NAME,
                        number : script.env.BUILD_NUMBER,
                        status : ctx.jobResult,
                        stages_result: stageResultsBody
                ],
                repo_url: script.scm.userRemoteConfigs[0].url,
                ci_url  : script.env.JENKINS_URL,
                tag_name : ctx.repoTag
        ]
        def jsonBody = groovy.json.JsonOutput.toJson(body)
        script.echo "jarvis request body: $jsonBody"
        script.httpRequest consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                httpMode: 'POST',
                requestBody: jsonBody,
                url: "${Constants.JARVIS_URL}webhooks/version/",
                validResponseCodes: '202'
    }


}
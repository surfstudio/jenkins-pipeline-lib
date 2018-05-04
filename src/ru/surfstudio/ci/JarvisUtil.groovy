package ru.surfstudio.ci

import ru.surfstudio.ci.pipeline.TagPipeline

class JarvisUtil {

    def static withJarvisToken(Object script, Closure closure){
        script.withCredentials([[$class: 'StringBinding', credentialsId: "jarvisApiToken", variable: "jarvisToken"]], closure)
    }

    /**
     * Работает внутри withJarvisToken()
     * @param script
     * @return http параметр с нужным токеном
     */
    def static getHttpParamToken(Object script){
        return "authToken=${script.env.jarvisToken}"
    }

    def static sendMessageToUser(Object script, String message, String userId, String idType) {
        withJarvisToken(script) {
            script.echo "Sending message to user: ${message}"
            def body = [
                    id_or_name    : userId,
                    message       : message,
                    message_format: "html",
                    as_task       : true,
                    id_type       : idType,
                    notify        : true
            ]
            def jsonBody = groovy.json.JsonOutput.toJson(body)

            CommonUtil.safe(script) {
                script.httpRequest consoleLogResponseBody: true,
                        contentType: 'APPLICATION_JSON',
                        httpMode: 'POST',
                        requestBody: jsonBody,
                        url: "${Constants.JARVIS_URL}message/?${getHttpParamToken(script)}",
                        validResponseCodes: '204'
            }
        }
    }

    def static sendMessageToGroup(Object script, String message, String projectId, String idType, boolean success) {
        withJarvisToken(script) {
            script.echo "Sending message to project: ${message}"
            def body = [
                    id_or_name    : projectId,
                    message       : message,
                    message_format: "html",
                    as_task       : true,
                    id_type       : idType,
                    notify        : true,
                    color         : success ? "green" : "red",
                    sender        : "Jarvis"
            ]
            def jsonBody = groovy.json.JsonOutput.toJson(body)
            CommonUtil.safe(script) {
                script.httpRequest consoleLogResponseBody: true,
                        contentType: 'APPLICATION_JSON',
                        httpMode: 'POST',
                        requestBody: jsonBody,
                        url: "${Constants.JARVIS_URL}notification/?${getHttpParamToken(script)}",
                        validResponseCodes: '204'
            }
        }
    }

    def static getMainBranch(Object script, String repoUrl){
        withJarvisToken(script) {
            def rawResponse = script.httpRequest consoleLogResponseBody: true,
                    httpMode: 'GET',
                    url: "${Constants.JARVIS_URL}repositories/branches/default?repo_url=${repoUrl}&${getHttpParamToken(script)}",
                    validResponseCodes: '200'
            def response = script.readJSON text: rawResponse.content
            return response.name
        }
    }

    def static changeTaskStatus(Object script, String newTaskStatus, String taskKey) {
        withJarvisToken(script) {
            script.httpRequest consoleLogResponseBody: true,
                    url: "${Constants.JARVIS_URL}issues/change-status/?${getHttpParamToken(script)}",
                    requestBody: "{\"status_name\": \"$newTaskStatus\", \"issue_key\": \"$taskKey\"}",
                    httpMode: 'POST',
                    contentType: 'APPLICATION_JSON',
                    validResponseCodes: '204'
        }
    }

    def static createVersionAndNotify(TagPipeline ctx) {
        def script = ctx.script
        withJarvisToken(script) {
            def stageResultsBody = []
            for (stage in ctx.stages) {
                if(stage.result) {
                    stageResultsBody.add([name: stage.name, status: stage.result])
                }
            }

            def body = [
                    build   : [
                            job_name     : script.env.JOB_NAME,
                            number       : script.env.BUILD_NUMBER,
                            status       : ctx.jobResult,
                            stages_result: stageResultsBody
                    ],
                    repo_url: script.scm.userRemoteConfigs[0].url,
                    ci_url  : script.env.JENKINS_URL,
                    tag_name: ctx.repoTag
            ]
            def jsonBody = groovy.json.JsonOutput.toJson(body)
            script.echo "jarvis request body: $jsonBody"
            script.httpRequest consoleLogResponseBody: true,
                    contentType: 'APPLICATION_JSON',
                    httpMode: 'POST',
                    requestBody: jsonBody,
                    url: "${Constants.JARVIS_URL}webhooks/version/?${getHttpParamToken(script)}",
                    validResponseCodes: '202'
        }
    }
}

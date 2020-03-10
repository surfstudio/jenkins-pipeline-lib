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
package ru.surfstudio.ci

import ru.surfstudio.ci.pipeline.tag.TagPipeline
import ru.surfstudio.ci.stage.StageWithResult

class JarvisUtil {

    def static withJarvisToken(Object script, Closure closure) {
        script.withCredentials([[$class: 'StringBinding', credentialsId: "jarvisApiToken", variable: "jarvisToken"]], closure)
    }

    /**
     * Работает внутри withJarvisToken()
     * @param script
     * @return http параметр с нужным токеном
     */
    def static getHttpParamToken(Object script) {
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
                        validResponseCodes: '200:299'
            }
        }
    }

    def static sendMessageToGroup(Object script, String message, String projectId, String idType, boolean success) {
        sendMessageToGroupUtil(script, message, projectId, idType, success ? "green" : "red")
    }

    def static sendMessageToGroup(Object script, String message, String projectId, String idType, String jobResult) {
        def color
        switch (jobResult) {
            case Result.SUCCESS:
                color = "green"
                break
            case Result.UNSTABLE:
                color = "yellow"
                break
            default:
                color = "red"
        }
        sendMessageToGroupUtil(script, message, projectId, idType, color)
    }

    private def static sendMessageToGroupUtil(Object script, String message, String projectId, String idType, String color) {
        withJarvisToken(script) {
            script.echo "Sending message to project: ${message}"
            def body = [
                    id_or_name    : projectId,
                    message       : message,
                    message_format: "html",
                    as_task       : true,
                    id_type       : idType,
                    notify        : true,
                    color         : color,
                    sender        : "Jarvis"
            ]
            def jsonBody = groovy.json.JsonOutput.toJson(body)
            CommonUtil.safe(script) {
                script.httpRequest consoleLogResponseBody: true,
                        contentType: 'APPLICATION_JSON',
                        httpMode: 'POST',
                        requestBody: jsonBody,
                        url: "${Constants.JARVIS_URL}notification/?${getHttpParamToken(script)}",
                        validResponseCodes: '200:299'
            }
        }
    }

    def static getMainBranch(Object script, String repoUrl) {
        withJarvisToken(script) {
            def rawResponse = script.httpRequest consoleLogResponseBody: true,
                    httpMode: 'GET',
                    url: "${Constants.JARVIS_URL}repositories/branches/default?repo_url=${repoUrl}&${getHttpParamToken(script)}",
                    validResponseCodes: '200:299'
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
                    validResponseCodes: '200:299'
        }
    }

    def static createVersionAndNotify(TagPipeline ctx) {
        def script = ctx.script
        withJarvisToken(script) {
            def stageResultsBody = []
            ctx.forStages { stage ->
                if (stage instanceof StageWithResult && stage.result) {
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
                    repo_url: ctx.repoUrl,
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
                    validResponseCodes: '200:299'
        }
    }
}

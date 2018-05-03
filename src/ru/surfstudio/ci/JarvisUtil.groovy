package ru.surfstudio.ci

import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.CommonUtil

class JarvisUtil {

    def static sendMessageToUser(Object script, String message, String userId, String idType) {
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
                    url: "${Constants.JARVIS_URL}message/",
                    validResponseCodes: '204'
        }
    }

    def static sendMessageToGroup(Object script, String message, String projectId, String idType, boolean success) {
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
                    url: "${Constants.JARVIS_URL}notification/",
                    validResponseCodes: '204'
        }
    }

    def static getMainBranch(Object script, String repoUrl){
        def rawResponse = script.httpRequest consoleLogResponseBody: true,
                httpMode: 'GET',
                url: "${Constants.JARVIS_URL}repositories/branches/default?repo_url=${repoUrl}",
                validResponseCodes: '200'
        def response = script.readJSON text: rawResponse.content
        return response.name
    }

    def static changeTaskStatus(Object script, String newTaskStatus, String taskKey) {
        script.httpRequest consoleLogResponseBody: true,
                url: "${Constants.JARVIS_URL}issues/change-status/",
                requestBody: "{\"status_name\": \"$newTaskStatus\", \"issue_key\": \"$taskKey\"}",
                httpMode: 'POST',
                contentType: 'APPLICATION_JSON',
                validResponseCodes: '204'
    }
}

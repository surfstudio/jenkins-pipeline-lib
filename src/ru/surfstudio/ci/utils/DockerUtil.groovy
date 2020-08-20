package ru.surfstudio.ci.utils

class DockerUtil {

    def static withRegistry(Object script,
                            String url,
                            String credentialsId,
                            Closure body) {
        if(credentialsId.startsWith("gcr:")){
            script.withCredentials([file(credentialsId: credentialsId, variable: 'registry_json_key')]) {
                script.sh "cat $script.registry_json_key | docker login -u _json_key --password-stdin $url"
                body()
                script.sh "docker logout $url"
            }
        } else {
            script.docker.withRegistry(url, credentialsId) {
                body()
            }
        }

    }
}

package ru.surfstudio.ci.utils

class DockerUtil {

    /**
     * Analog of docker.withRegistry(url, credentialsId) which support gcloud registry
     * docker.withRegistry not work with gcloud credentials, even with Google Container Registry Auth Plugin https://issues.jenkins-ci.org/browse/JENKINS-50557
     */
    def static withRegistry(Object script,
                            String url,
                            String credentialsId,
                            Closure body) {
        if(credentialsId.startsWith("gcr:")){
            def clearCredId = credentialsId.replace("gcr:", "")
            script.withCredentials([script.file(credentialsId: clearCredId, variable: 'registry_json_key')]) {
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

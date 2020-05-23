package ru.surfstudio.ci.pipeline.helper

final class DockerRegistryHelper {
    private DockerRegistryHelper() {
    }
    def static withRegistryCredentials(Object script, String registryUrl, Closure body) {
            script.withCredentials([script.file(credentialsId: "google-container-registry-service-account", variable: 'GCRKEY')]) {
                body()
            }
    }

    def static buildDockerImage(Object script, String projectId, String registryUrl, String imageTag) {
        withRegistryCredentials(script, registryUrl){
            scrip.sh "pwd"
            script.sh "ls build"
            def image = script.docker.build("$projectId:$imageTag","./")
            image.push()
        }
    }
}

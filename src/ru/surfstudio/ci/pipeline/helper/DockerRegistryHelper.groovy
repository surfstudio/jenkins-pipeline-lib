package ru.surfstudio.ci.pipeline.helper

final class DockerRegistryHelper {
    private DockerRegistryHelper() {
    }
    def static withRegistryCredentials(Object script, String registryUrl, Closure body) {
            script.withCredentials([script.file(credentialsId: "google-container-registry-service-account", variable: 'GCRKEY')]) {
                script.sh "docker login -u _json_key -p \"`cat \$GCRKEY`\" $registryUrl"
                body()
            }
    }

    def static buildDockerImage(Object script, String projectId, String registryUrl, String imageTag) {
        withRegistryCredentials(script, registryUrl){
            script.sh "pwd"
            script.sh "ls"
            def image = script.docker.build("$registryUrl/$projectId:$imageTag","./")
            image.push()
        }
    }
}

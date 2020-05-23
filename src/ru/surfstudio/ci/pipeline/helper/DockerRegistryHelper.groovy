package ru.surfstudio.ci.pipeline.helper

final class DockerRegistryHelper {
    private DockerRegistryHelper() {
    }
    def static withRegistryCredentials(Object script, String registryUrl, Closure body) {
        script.echo(script.credentials("google-container-registry-service-account"))
        script.docker.withRegistry(registryUrl, 'google-container-registry-service-account') {
            body()
        }
    }

    def static buildDockerImage(Object script, String projectId, String registryUrl, String imageTag) {
        withRegistryCredentials(script, registryUrl){
            def image = script.docker.build("$projectId:$imageTag","./")
            image.push()
        }
    }
}

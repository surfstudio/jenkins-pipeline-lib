package ru.surfstudio.ci.pipeline.helper

final class DockerHelper {
    private DockerHelper() {
    }
    def static withRegistryCredentials(Object script, String registryUrl, Closure body) {
            script.withCredentials([script.file(credentialsId: "google-container-registry-service-account", variable: 'GCRKEY')]) {
                script.sh "docker login -u _json_key -p \"`cat \$GCRKEY`\" $registryUrl"
                body()
            }
    }

    def static buildDockerImageAndPush(Object script, String projectId, String registryUrl, String pathToFile, List<String> imageTags) {
        withRegistryCredentials(script, registryUrl){
            def image = script.docker.build("$registryUrl/$projectId", pathToFile)
            for (String imageTag : imageTags) {
                image.push(imageTag)
            }
        }
    }
}

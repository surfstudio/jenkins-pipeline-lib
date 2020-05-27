package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.pipeline.helper.DockerHelper
import ru.surfstudio.ci.stage.StageStrategy

class TagPipelineBackend extends TagPipeline {
    public buildGradleTask = "clean assemble"
    public unitTestGradleTask = "test"
    public DOCKER_BUILD_PUBLISH_IMAGE = "Build and publish docker image"

    public unitTestResultPathXml = "build/test-results/test/*.xml"
    public unitTestResultDirHtml = "build/reports/tests/test"
    public pathToDockerfile = "./"
    public registryUrl = "eu.gcr.io"
    public registryPathAndProjectId = ""
    public dockerImageForBuild = null

    public gradleBuildFile = "build.gradle.kts"
    public appVersionNameGradleVar = "version"

    TagPipelineBackend(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.backendNode
        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        script.echo "$repoTag"


        stages = [
                stage(CHECKOUT, false) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(BUILD) {
                    DockerHelper.runStageInsideDocker(script, dockerImageForBuild, {
                        BackendPipelineHelper.buildStageBodyBackend(script, buildGradleTask)
                    })
                },
                stage(UNIT_TEST) {
                    DockerHelper.runStageInsideDocker(script, dockerImageForBuild, {
                        BackendPipelineHelper.runUnitTests(script, unitTestGradleTask, unitTestResultPathXml, unitTestResultDirHtml)
                    })
                },
                stage(DOCKER_BUILD_PUBLISH_IMAGE, StageStrategy.SKIP_STAGE) {
                    List<String> tags = new ArrayList<String>()
                    String fullCommitHash = RepositoryUtil.getCurrentCommitHash(script)
                    if (fullCommitHash != null && !fullCommitHash.isEmpty())
                        tags.add("dev-${fullCommitHash.reverse().take(8)}")
                    tags.add("dev")
                    DockerHelper.buildDockerImageAndPush(script, registryPathAndProjectId, registryUrl, pathToDockerfile, tags)
                }
        ]
        finalizeBody = { finalizeStageBody(this) }
    }
}
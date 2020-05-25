package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.pipeline.helper.DockerRegistryHelper
import ru.surfstudio.ci.stage.StageStrategy

/**
 * In a case when you need running some steps into specific environment (for instance build and run tests using jvm 11)
 * you can use {@link PrPipelineBackend#runInsideDocker} method, previously you have to set image name {@link PrPipelineBackend#dockerImage}.<p>
 * Build and Unit_test steps are wrapped by {@link PrPipelineBackend#runInsideDocker} method, therefore if you'll set {@link PrPipelineBackend#dockerImage} field
 * that steps will run inside docker. <p>
 *     For Instance: <p>
 * <pre>
 * def pipeline = new PrPipelineBackend(this)
 * pipeline.init()
 * pipeline.dockerImage = "gradle:6.0.1-jdk11"
 * pipeline.run()
 * </pre>
 */
class PrPipelineBackend extends PrPipeline {
    private boolean hasChanges = false
    public buildGradleTask = "clean assemble"
    public unitTestGradleTask = "test"
    public DOCKER_BUILD_PUBLISH_IMAGE = "Build and publish docker image"

    public unitTestResultPathXml = "build/test-results/test/*.xml"
    public unitTestResultDirHtml = "build/reports/tests/test"
    public pathToDockerfile = "./"
    public registryUrl = "eu.gcr.io"
    public registryPathAndProjectId = ""

    public String dockerImage = null

    PrPipelineBackend(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.backendNode
        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT, false) {
                    checkout(script, repoUrl, sourceBranch, repoCredentialsId)
                    saveCommitHashAndCheckSkipCi(script, targetBranchChanged)
                    abortDuplicateBuildsWithDescription(this)
                },
                stage(CODE_STYLE_FORMATTING) {
                    AndroidPipelineHelper.ktlintFormatStageAndroid(script, sourceBranch, destinationBranch)
                    hasChanges = AndroidPipelineHelper.checkChangesAndUpdate(script, repoUrl, repoCredentialsId, sourceBranch)
                },
                stage(UPDATE_CURRENT_COMMIT_HASH_AFTER_FORMAT, false) {
                    if (hasChanges) {
                        RepositoryUtil.saveCurrentGitCommitHash(script)
                    }
                },
                stage(PRE_MERGE) {
                    mergeLocal(script, destinationBranch)
                },
                stage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    runInsideDocker {
                        BackendPipelineHelper.buildStageBodyBackend(script, buildGradleTask)
                    }
                },
                stage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    runInsideDocker {
                        BackendPipelineHelper.runUnitTests(script, unitTestGradleTask, unitTestResultPathXml, unitTestResultDirHtml)
                    }
                },
                stage(DOCKER_BUILD_PUBLISH_IMAGE, StageStrategy.SKIP_STAGE) {
                       DockerRegistryHelper.buildDockerImageAndPush(script, registryPathAndProjectId, registryUrl,pathToDockerfile,"test")
                }]
        finalizeBody = { finalizeStageBody(this) }
    }

    def runInsideDocker(Closure closure) {
        if(dockerImage != null && !dockerImage.isEmpty())
            script.docker.image(dockerImage).inside(closure)
        else
            closure.call()
    }
}
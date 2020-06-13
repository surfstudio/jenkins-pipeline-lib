package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy

/**
 * In a case when you need running some steps into specific environment (for instance build and run tests using jvm 11)
 * you can use {@link ru.surfstudio.ci.pipeline.pr.PrPipelineBackend#buildInsideDocker(Closure)} method, previously you have to set image name {@link PrPipelineBackend#dockerImageForBuild}.<p>
 * Build and Unit_test steps are wrapped by {@link ru.surfstudio.ci.pipeline.pr.PrPipelineBackend#buildInsideDocker(Closure)} method, therefore if you'll set {@link PrPipelineBackend#dockerImageForBuild} field
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

    public unitTestResultPathXml = "build/test-results/test/*.xml"
    public unitTestResultDirHtml = "build/reports/tests/test"

    public DOCKER_BUILD_WRAPPED_STAGES = "Executing stages inside docker"
    public dockerImageForBuild = "gradle:6.0.1-jdk11"
    public dockerArguments = null

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
                docker(DOCKER_BUILD_WRAPPED_STAGES, dockerImageForBuild, dockerArguments,
                        [
                                stage(CHECKOUT, false) {
                                    standardCheckoutStageBody()
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
                                    BackendPipelineHelper.buildStageBodyBackend(script, buildGradleTask)
                                },
                                stage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                                    BackendPipelineHelper.runUnitTests(script, unitTestGradleTask, unitTestResultPathXml, unitTestResultDirHtml)
                                }
                        ])
        ]
        finalizeBody = { finalizeStageBody(this) }
    }
}
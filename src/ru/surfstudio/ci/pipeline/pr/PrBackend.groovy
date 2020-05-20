package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy

class PrBackend extends PrPipeline {
    private boolean hasChanges = false
    public buildGradleTask = "clean assemble"
    public unitTestGradleTask = "test"

    public unitTestResultPathXml = "build/test-results/test/*.xml"
    public unitTestResultDirHtml = "build/reports/tests/test"


    PrBackend(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.backendNode
        script.agent{
            docker {
                image 'gradle:6.0.1-jdk11'
                label 'android'
            }
        }
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
                stage(BUILD) {
                    BackendPipelineHelper.buildStageBodyBackend(
                            script, buildGradleTask
                    )
                },
                stage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.unitTestStageBodyAndroid(script, unitTestGradleTask, unitTestResultPathXml, unitTestResultDirHtml)
                }
        ]
        finalizeBody = { finalizeStageBody(this) }
    }
}

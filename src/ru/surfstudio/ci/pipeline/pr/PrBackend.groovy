package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.stage.Stage
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

    @Override
    def run() {
        CommonUtil.fixVisualizingStagesInParallelBlock(script)
        try {
            def initStage = stage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR, false, createInitStageBody())
            initStage.execute(script, this)
            script.node(node) {
                if (CommonUtil.notEmpty(node)) {
                    script.echo "Switch to node ${node}: ${script.env.NODE_NAME}"
                }

                script.docker.image('gradle:6.0.1-jdk11').internal {
                    for (Stage stage : stages) {
                        stage.execute(script, this)
                    }
                }
            }
        } finally {
            jobResult = calculateJobResult(stages)
            if (jobResult == Result.ABORTED || jobResult == Result.FAILURE) {
                script.echo "Job stopped, see reason above ^^^^"
            }
            script.echo "Finalize build:"
            printStageResults()
            script.echo "Current job result: ${script.currentBuild.result}"
            script.echo "Try apply job result: ${jobResult}"
            script.currentBuild.result = jobResult
            //нельзя повышать статус, то есть если раньше был установлен failed или unstable, нельзя заменить на success
            script.echo "Updated job result: ${script.currentBuild.result}"
            if (finalizeBody) {
                script.echo "Start finalize body"
                finalizeBody()
                script.echo "End finalize body"
            }
        }
    }

}

package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

class TestAutoAbortedPipeline extends AutoAbortedPipeline {


    def String branchName = ""

    TestAutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def initInternal() {

        abortStrategy = AbortDuplicateStrategy.SELF
        //configuration
        node = NodeProvider.getAndroidNode()

        preExecuteStageBody = {}
        postExecuteStageBody = {}


        stages = [
                createStage("Init", StageStrategy.FAIL_WHEN_STAGE_ERROR){
                    applyParameterIfNotEmpty(script, "branchName", script.params.branchName, {
                        value -> this.branchName = value
                    })
                },
                createStage("Checkout", StageStrategy.FAIL_WHEN_STAGE_ERROR){
                    script.sleep(20)
                },
                createStage("IntegrationTest", StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {

                }
        ]

        finalizeBody = {

        }
    }

    @Override
    def final String getBuildIdentifier() {
        return branchName
    }


}
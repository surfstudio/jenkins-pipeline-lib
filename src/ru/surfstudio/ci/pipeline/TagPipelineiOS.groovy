package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.TagiOSStages
import ru.surfstudio.ci.stage.body.PrStages

class PrPipelineiOS extends PrPipeline {

    PrPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getiOSNode()
        stages = [
                createStage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.initStageBody(this)
                },
                createStage(PRE_MERGE, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.preMergeStageBody(script, sourceBranch, destinationBranch)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagiOSStages.buildStageBodyiOS(script)
                },
                createStage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    TagiOSStages.unitTestStageBodyiOS(script)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    TagiOSStages.instrumentationTestStageBodyiOS(script)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    TagiOSStages.staticCodeAnalysisStageBodyiOS(script)
                }
        ]
        finalizeBody = { PrStages.finalizeStageBody(this) }
    }
}
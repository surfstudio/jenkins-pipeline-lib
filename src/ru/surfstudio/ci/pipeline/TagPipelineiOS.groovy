package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommoniOSStages
import ru.surfstudio.ci.stage.body.TagStages

class TagPipelineiOS extends TagPipeline {

    TagPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getiOSNode()
        stages = [
                createStage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagStages.initStageBody(this)
                },
                createStage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagStages.checkoutStageBody(script, repoTag)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommoniOSStages.buildStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId
                    )
                },
                createStage(UNIT_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommoniOSStages.unitTestStageBodyiOS(script)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommoniOSStages.instrumentationTestStageBodyiOS(script)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommoniOSStages.staticCodeAnalysisStageBodyiOS(script)
                },
                createStage(BETA_UPLOAD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    TagStages.betaUploadStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId
                    )
                },

        ]
        finalizeBody = { TagStages.finalizeStageBody(this) }
    }
}
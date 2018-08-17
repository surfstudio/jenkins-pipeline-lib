package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.pipeline.helper.iOSPipelineHelper

class PrPipelineiOS extends PrPipeline {

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    PrPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def init() {
        propertiesProvider = { PrPipeline.properties(this) }
        node = NodeProvider.getiOSNode()

        preExecuteStageBody = PrPipeline.getPreExecuteStageBody(script, repoUrl)
        postExecuteStageBody = PrPipeline.getPostExecuteStageBody(script, repoUrl)

        initializeBody = {  PrPipeline.initBody(this) }
        stages = [
                createStage(PRE_MERGE, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrPipeline.preMergeStageBody(script, repoUrl, sourceBranch, destinationBranch, repoCredentialsId)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.buildStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId
                    )
                },
                createStage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.unitTestStageBodyiOS(script)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.instrumentationTestStageBodyiOS(script)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.staticCodeAnalysisStageBodyiOS(script)
                }
        ]
        finalizeBody = { PrPipeline.debugFinalizeStageBody(this) }
    }
}
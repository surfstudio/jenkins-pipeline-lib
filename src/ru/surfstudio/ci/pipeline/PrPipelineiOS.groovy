package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommoniOSStages
import ru.surfstudio.ci.stage.body.PrStages

class PrPipelineiOS extends PrPipeline {

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    PrPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def initInternal() {
        node = NodeProvider.getiOSNode()

        preExecuteStageBody = { stage -> if(stage.name != PRE_MERGE) CommonUtil.getBitbucketNotifyPreExecuteStageBody(script) }
        postExecuteStageBody = { stage -> if(stage.name != PRE_MERGE) CommonUtil.getBitbucketNotifyPostExecuteStageBody(script) }

        initStageBody = { PrStages.initStageBody(this) }
        stages = [
                createStage(PRE_MERGE, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    PrStages.preMergeStageBody(script, sourceBranch, destinationBranch)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    CommoniOSStages.buildStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId
                    )
                },
                createStage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommoniOSStages.unitTestStageBodyiOS(script)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommoniOSStages.instrumentationTestStageBodyiOS(script)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    CommoniOSStages.staticCodeAnalysisStageBodyiOS(script)
                }
        ]
        finalizeBody = { PrStages.debugFinalizeStageBody(this) }
    }
}
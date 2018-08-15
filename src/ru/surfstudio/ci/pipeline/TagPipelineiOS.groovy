package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommoniOSStages
import ru.surfstudio.ci.stage.body.TagStages

class TagPipelineiOS extends TagPipeline {

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"
    public betaUploadConfigArgument = "config"

    // Значение, которое передастся в Makefile скрипт beta пл ключу из betaUploadConfigArgument
    public betaUploadConfigValue = ""

    String getBuildConfigValue() {
        def resultConfigValue = betaUploadConfigValue
        if (!resultConfigValue?.trim()) {
            def matchValue = this.repoTag=~/.*-([^0-9]+)[0-9]/
            matchValue.each { resultConfigValue = it[1] }
        }
        return resultConfigValue
    }

    TagPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def initInternal() {
        node = NodeProvider.getiOSNode()

        preExecuteStageBody = { stage ->
            if(stage.name != CHECKOUT) RepositoryUtil.notifyBitbucketAboutStageStart(script, stage.name)
        }
        postExecuteStageBody = { stage ->
            if(stage.name != CHECKOUT) RepositoryUtil.notifyBitbucketAboutStageFinish(script, stage.name, stage.result)
        }

        initStageBody = { TagStages.initStageBody(this) }
        stages = [
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
                        iOSCertfileCredentialId,
                        betaUploadConfigArgument,
                        getBuildConfigValue()
                    )
                },

        ]
        finalizeBody = { TagStages.debugFinalizeStageBody(this) }
    }
}
package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.pipeline.helper.iOSPipelineHelper

class TagPipelineiOS extends TagPipeline {

    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"
    public betaUploadConfigArgument = "config"

    // Значение, которое передастся в Makefile скрипт beta пл ключу из betaUploadConfigArgument
    public betaUploadConfigValue = ""

    TagPipelineiOS(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getiOSNode()

        preExecuteStageBody = getPreExecuteStageBody(script, repoUrl)
        postExecuteStageBody = getPostExecuteStageBody(script, repoUrl)

        initializeBody = {  initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                createStage(CHECKOUT, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                createStage(BUILD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.buildStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId
                    )
                },
                createStage(UNIT_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.unitTestStageBodyiOS(script)
                },
                createStage(INSTRUMENTATION_TEST, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.instrumentationTestStageBodyiOS(script)
                },
                createStage(STATIC_CODE_ANALYSIS, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    iOSPipelineHelper.staticCodeAnalysisStageBodyiOS(script)
                },
                createStage(BETA_UPLOAD, StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                    betaUploadStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId,
                        betaUploadConfigArgument,
                        getBuildConfigValue()
                    )
                },

        ]
        finalizeBody = { debugFinalizeStageBody(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ ======================================================

    String getBuildConfigValue() {
        def resultConfigValue = betaUploadConfigValue
        if (!resultConfigValue?.trim()) {
            def matchValue = this.repoTag=~/.*-([^0-9]+)[0-9]/
            matchValue.each { resultConfigValue = it[1] }
        }
        return resultConfigValue
    }

    def static betaUploadStageBodyiOS(Object script, String keychainCredenialId, String certfileCredentialId, String betaUploadConfigArgument, String betaUploadConfigValue) {
        script.withCredentials([
                script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
                script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
        ]) {

            CommonUtil.shWithRuby(script, 'security -v unlock-keychain -p $KEYCHAIN_PASS')
            CommonUtil.shWithRuby(script, 'security import "$DEVELOPER_P12_KEY" -P "" -A')
            CommonUtil.shWithRuby(script, 'security set-key-partition-list -S apple-tool:,apple: -s -k $KEYCHAIN_PASS ~/Library/Keychains/login.keychain-db')
            CommonUtil.shWithRuby(script, 'security import "$DEVELOPER_P12_KEY" -P "" -A')

            CommonUtil.shWithRuby(script, "gem install bundler")

            CommonUtil.shWithRuby(script, "make init")
            CommonUtil.shWithRuby(script, "make beta ${betaUploadConfigArgument}=${betaUploadConfigValue}")
        }
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================



}
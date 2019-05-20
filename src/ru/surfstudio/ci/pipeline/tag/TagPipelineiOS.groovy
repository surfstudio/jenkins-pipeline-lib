/*
  Copyright (c) 2018-present, SurfStudio LLC.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
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

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = {  initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT, false) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(VERSION_UPDATE) {
                    script.echo "stage not specified" //todo
                },
                stage(BUILD) {
                    iOSPipelineHelper.buildStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId
                    )
                },
                stage(UNIT_TEST) {
                    iOSPipelineHelper.unitTestStageBodyiOS(script)
                },
                stage(INSTRUMENTATION_TEST) {
                    iOSPipelineHelper.instrumentationTestStageBodyiOS(script)
                },
                stage(STATIC_CODE_ANALYSIS) {
                    iOSPipelineHelper.staticCodeAnalysisStageBodyiOS(script)
                },
                stage(BETA_UPLOAD) {
                    betaUploadStageBodyiOS(script,
                        iOSKeychainCredenialId,
                        iOSCertfileCredentialId,
                        betaUploadConfigArgument,
                        getBuildConfigValue()
                    )
                },
                stage(VERSION_PUSH, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    script.echo "stage not specified" //todo temp
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

            CommonUtil.shWithRuby(script, "gem install bundler -v 1.17.3")

            CommonUtil.shWithRuby(script, "make init")
            CommonUtil.shWithRuby(script, "make beta ${betaUploadConfigArgument}=${betaUploadConfigValue}")
        }
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================



}

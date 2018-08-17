package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.pipeline.helper.iOSPipelineHelper

/**
 *  @Deprecated see {@link ru.surfstudio.ci.pipeline.helper.iOSPipelineHelper}
 */
@Deprecated
class CommoniOSStages {

    @Deprecated
    def static buildStageBodyiOS(Object script, String keychainCredenialId, String certfileCredentialId) {
        iOSPipelineHelper.buildStageBodyiOS(script, keychainCredenialId, certfileCredentialId)
    }

    @Deprecated
    def static unitTestStageBodyiOS(Object script) {
        iOSPipelineHelper.unitTestStageBodyiOS(script)
    }

    @Deprecated
    def static instrumentationTestStageBodyiOS(Object script) {
        iOSPipelineHelper.instrumentationTestStageBodyiOS(script)
    }

    @Deprecated
    def static staticCodeAnalysisStageBodyiOS(Object script) {
        iOSPipelineHelper.staticCodeAnalysisStageBodyiOS(script)
    }


}
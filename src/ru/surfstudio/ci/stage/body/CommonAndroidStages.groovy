package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.AndroidUtil
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper

/**
 *  @Deprecated see {@link AndroidPipelineHelper}
 */
@Deprecated
class CommonAndroidStages {

    @Deprecated
    def static buildStageBodyAndroid(Object script, String buildGradleTask) {
        AndroidPipelineHelper.buildStageBodyAndroid(script, buildGradleTask)
    }

    @Deprecated
    def static buildWithCredentialsStageBodyAndroid(Object script,
                                                    String buildGradleTask,
                                                    String keystoreCredentials,
                                                    String keystorePropertiesCredentials) {
        AndroidPipelineHelper.buildWithCredentialsStageBodyAndroid(script, buildGradleTask, keystoreCredentials, keystorePropertiesCredentials)
    }

    @Deprecated
    def static unitTestStageBodyAndroid(Object script,
                                        String unitTestGradleTask,
                                        String testResultPathXml,
                                        String testResultPathDirHtml) {
        AndroidPipelineHelper.unitTestStageBodyAndroid(script, unitTestGradleTask, testResultPathXml, testResultPathDirHtml)
    }

    @Deprecated
    def static instrumentationTestStageBodyAndroid(Object script, String testGradleTask, String testResultPathXml, String testResultPathDirHtml) {
        AndroidPipelineHelper.instrumentationTestStageBodyAndroid(script, testGradleTask, testResultPathXml, testResultPathDirHtml)
    }

    @Deprecated
    def static staticCodeAnalysisStageBody(Object script) {
        AndroidPipelineHelper.staticCodeAnalysisStageBody(script)
    }


}
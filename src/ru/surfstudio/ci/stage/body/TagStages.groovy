package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.pipeline.tag.TagPipeline
import ru.surfstudio.ci.pipeline.tag.TagPipelineAndroid
import ru.surfstudio.ci.pipeline.tag.TagPipelineiOS

@Deprecated
class TagStages {

    @Deprecated
    def static initStageBody(TagPipeline ctx) {
        TagPipeline.initBody(ctx)
    }

    @Deprecated
    def static checkoutStageBody(Object script,  String url, String repoTag, String credentialsId) {
        TagPipeline.checkoutStageBody(script, url, repoTag, credentialsId)
    }

    @Deprecated
    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        TagPipelineAndroid.betaUploadWithKeystoreStageBodyAndroid(script, betaUploadGradleTask)
    }

    @Deprecated
    def static betaUploadWithKeystoreStageBodyAndroid(Object script,
                                                      String betaUploadGradleTask,
                                                      String keystoreCredentials,
                                                      String keystorePropertiesCredentials) {
        TagPipelineAndroid.betaUploadWithKeystoreStageBodyAndroid(script, betaUploadGradleTask, keystoreCredentials, keystorePropertiesCredentials)
    }

    @Deprecated
    def static betaUploadStageBodyiOS(Object script, String keychainCredenialId, String certfileCredentialId, String betaUploadConfigArgument, String betaUploadConfigValue) {
        TagPipelineiOS.betaUploadStageBodyiOS(script, keychainCredenialId, certfileCredentialId, betaUploadConfigArgument, betaUploadConfigValue)
    }

    @Deprecated
    def static prepareMessageForPipeline(TagPipeline ctx, Closure handler) {
        TagPipeline.prepareMessageForPipeline(ctx, handler)
    }

    @Deprecated
    def static finalizeStageBody(TagPipeline ctx){
        TagPipeline.finalizeStageBody(ctx)
    }

    def static debugFinalizeStageBody(TagPipeline ctx) {
        TagPipeline.debugFinalizeStageBody(ctx)
    }

}
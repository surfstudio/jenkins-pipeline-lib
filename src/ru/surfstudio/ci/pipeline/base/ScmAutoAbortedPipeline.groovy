package ru.surfstudio.ci.pipeline.base

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.RepositoryUtil

/**
 *
 */
abstract class ScmAutoAbortedPipeline extends AutoAbortedPipeline {

    //required configuration
    public repoUrl = ""
    public repoCredentialsId = Constants.BITBUCKET_BUILDER_CREDENTIALS_ID

    ScmAutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def run() {
        initAndCheckRepositoryConfiguration(this)
        return super.run()
    }

    def static initAndCheckRepositoryConfiguration(ScmAutoAbortedPipeline ctx) {
        def script = ctx.script
        RepositoryUtil.tryExtractInitialRemoteConfig(script) { url, credentialsId ->
            ctx.repoUrl = url
            ctx.repoCredentialsId = credentialsId
            script.echo "Remote repository configurations sets from initial scm"
        }
        CommonUtil.checkConfigurationParameterDefined(script, ctx.repoUrl, "repoUrl")
        CommonUtil.checkConfigurationParameterDefined(script, ctx.repoCredentialsId, "repoCredentialsId")
    }
}

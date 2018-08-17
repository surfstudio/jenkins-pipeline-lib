package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.RepositoryUtil

/**
 *
 */
abstract class ScmPipeline extends Pipeline {

    //required configuration
    public repoUrl = ""
    public repoCredentialsId = Constants.BITBUCKET_BUILDER_CREDENTIALS_ID

    ScmPipeline(Object script) {
        super(script)
    }

    @Override
    Closure getFullInitStageBody() {
        def standardFullInitBody = super.getFullInitStageBody()
        return {
            initAndCheckRepositoryConfiguration(this)
            standardFullInitBody()
        }
    }

    def static initAndCheckRepositoryConfiguration(ScmPipeline ctx) {
        def script = ctx.script
        RepositoryUtil.tryExtractInitialRemoteConfig(script) { url, credentialsId ->
            ctx.repoUrl = url
            ctx.repoCredentialsId = credentialsId
            script.echo "Remote repository configurations sets from initial scm"
        }
        CommonUtil.checkPipelineParameterDefined(script, ctx.repoUrl, "repoUrl")
        CommonUtil.checkPipelineParameterDefined(script, ctx.repoCredentialsId, "repoCredentialsId")
    }
}

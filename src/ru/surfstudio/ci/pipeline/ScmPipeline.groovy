package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.RepositoryUtil

/**
 * Базовый pipeline для Jobs, которые используют исходный код из удаленного репозитория
 * Если JenkinsFile выгружается из репо, то пареметры repoUrl и repoCredentialsId инициализируются из этого репо
 * Если pipeline описан в настройках самого Job, то пареметр repoUrl (опционально и repoCredentialsId) должен быть
 * явно указан между вызовами init() и run()
 */
abstract class ScmPipeline extends Pipeline {

    //required initial configuration
    public repoUrl = ""
    public repoCredentialsId = Constants.BITBUCKET_BUILDER_CREDENTIALS_ID

    ScmPipeline(Object script) {
        super(script)
    }

    @Override
    Closure createInitStageBody() {
        def standardFullInitBody = super.createInitStageBody()
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

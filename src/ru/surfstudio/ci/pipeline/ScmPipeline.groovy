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
package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.utils.CommonUtil
import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.utils.RepositoryUtil

/**
 * Базовый pipeline для Jobs, которые используют исходный код из удаленного репозитория
 * Если JenkinsFile выгружается из репо, то пареметры repoUrl и repoCredentialsId инициализируются из этого репо
 * Если pipeline описан в настройках самого Job, то пареметр repoUrl (опционально и repoCredentialsId) должен быть
 * явно указан между вызовами init() и run()
 *
 * Для checkout не следует использовать step "checkout", поскольку он не работает с pipelines, описанными в настройках Job
 * Тоже относится и для, нампример script.scm.userRemoteConfigs
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

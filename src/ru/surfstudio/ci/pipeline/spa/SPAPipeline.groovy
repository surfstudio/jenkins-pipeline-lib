/*
  Copyright (c) 2021-present, SurfStudio LLC.

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
package ru.surfstudio.ci.pipeline.spa

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.base.LogRotatorUtil

import static ru.surfstudio.ci.CommonUtil.extractValueFromEnvOrParamsAndRun

abstract class SPAPipeline extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = "Checkout"
    public static final String CPD_CHECK = 'CPD Check'

    //scm
    private static final UNDEFINED_BRANCH = "<undefined>"
    public sourceBranch = UNDEFINED_BRANCH

    //region customization of stored artifacts

    // artifacts are only kept up to this days
    public int artifactDaysToKeep = 3
    // only this number of builds have their artifacts kept
    public int artifactNumToKeep = 10
    // history is only kept up to this days
    public int daysToKeep = 30
    // only this number of build logs are kept
    public int numToKeep = 100

    private static int ARTIFACTS_DAYS_TO_KEEP_MAX_VALUE = 5
    private static int ARTIFACTS_NUM_TO_KEEP_MAX_VALUE = 20
    private static int DAYS_TO_KEEP_MAX_VALUE = 30
    private static int NUM_TO_KEEP_MAX_VALUE = 100

    //endregion

    SPAPipeline(Object script) {
        super(script)
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(SPAPipeline ctx) {
        initBodyWithOutAbortDuplicateBuilds(ctx)
        RepositoryUtil.notifyGithubAboutStagePending(ctx.script, ctx.repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE, ctx.sourceBranch)
    }

    def static initBodyWithOutAbortDuplicateBuilds(SPAPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)

        extractValueFromEnvOrParamsAndRun(script, SOURCE_BRANCH_PARAMETER) {
            value -> ctx.sourceBranch = value
        }

        if (!ctx.sourceBranch || ctx.sourceBranch == UNDEFINED_BRANCH) {
            ctx.sourceBranch = JarvisUtil.getMainBranch(script, ctx.repoUrl)
        }

        if (ctx.sourceBranch.contains("origin/")) {
            ctx.sourceBranch = ctx.sourceBranch.replace("origin/", "")
        }

        CommonUtil.setBuildDescription(script, ctx.buildDescription())
    }

    def static abortDuplicateBuildsWithDescription(SPAPipeline ctx) {
        CommonUtil.abortDuplicateBuildsWithDescription(ctx.script, AbortDuplicateStrategy.ANOTHER, ctx.buildDescription())
    }

    def standardCheckoutStageBody() {
        checkout(script, repoUrl, sourceBranch, repoCredentialsId)
        abortDuplicateBuildsWithDescription(this)
    }

    def static checkout(Object script, String url, String sourceBranch, String credentialsId) {
        //script.sh 'git config --global user.name "Jenkins"'
        //script.sh 'git config --global user.email "jenkins@surfstudio.ru"'
        script.git(
                url: url,
                credentialsId: credentialsId
        )
        script.sh "git checkout -B $sourceBranch origin/$sourceBranch"
        RepositoryUtil.saveCurrentGitCommitHash(script)
    }

    def static finalizeStageBody(SPAPipeline ctx) {
        RepositoryUtil.notifyGithubAboutStageFinish(ctx.script, ctx.repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE, ctx.jobResult, ctx.sourceBranch)
        prepareMessageForPipeline(ctx, { message ->
            //todo
        })
    }

    String buildDescription() {
        return sourceBranch
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String SOURCE_BRANCH_PARAMETER = 'branchName_0'

    static List<Object> properties(SPAPipeline ctx) {
        def script = ctx.script
        return [
                buildDiscarder(ctx, script),
                parameters(script)
        ]
    }

    def static buildDiscarder(SPAPipeline ctx, script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.ARTIFACTS_DAYS_TO_KEEP_NAME,
                                ctx.artifactDaysToKeep,
                                ARTIFACTS_DAYS_TO_KEEP_MAX_VALUE
                        ),
                        artifactNumToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.ARTIFACTS_NUM_TO_KEEP_NAME,
                                ctx.artifactNumToKeep,
                                ARTIFACTS_NUM_TO_KEEP_MAX_VALUE
                        ),
                        daysToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.DAYS_TO_KEEP_NAME,
                                ctx.daysToKeep,
                                DAYS_TO_KEEP_MAX_VALUE
                        ),
                        numToKeepStr: LogRotatorUtil.getActualParameterValue(
                                script,
                                LogRotatorUtil.NUM_TO_KEEP_NAME,
                                ctx.numToKeep,
                                NUM_TO_KEEP_MAX_VALUE
                        )
                )
        )
    }

    def static parameters(script) {
        return script.parameters([
                script.string(
                        name: SOURCE_BRANCH_PARAMETER,
                        description: 'Ветка с исходным кодом'
                )
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}

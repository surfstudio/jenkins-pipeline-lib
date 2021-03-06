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

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result

import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.base.LogRotatorUtil
import ru.surfstudio.ci.stage.SimpleStage

import java.util.regex.Pattern

import static ru.surfstudio.ci.CommonUtil.extractValueFromEnvOrParamsAndRun

abstract class TagPipeline extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = 'Checkout'
    public static final String VERSION_UPDATE = 'Version Update'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'
    public static final String BETA_UPLOAD = 'Beta Upload'
    public static final String VERSION_PUSH = 'Version Push'

    //scm
    public tagRegexp = /(.*)?\d{1,4}\.\d{1,4}\.\d{1,4}(.*)?/
    public tagHash = ""
    public repoTag = ""
    //будет выбрана первая подходящая ветка
    public branchesPatternsForAutoChangeVersion = [/^origin\/dev\/.*/, /^origin\/feature\/.*/]

    //logic for customize
    public Closure applyStrategiesFromParams = { ctx -> //todo нужна ли вообще эта логика?
        def params = script.params
        CommonUtil.applyStrategiesFromParams(ctx, [
                (UNIT_TEST)           : params[UNIT_TEST_STAGE_STRATEGY_PARAMETER],
                (INSTRUMENTATION_TEST): params[INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER],
                (STATIC_CODE_ANALYSIS): params[STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER],
                (BETA_UPLOAD)         : params[BETA_UPLOAD_STAGE_STRATEGY_PARAMETER],
        ])
    }

    //region customization of stored artifacts

    // artifacts are only kept up to this days
    public int artifactDaysToKeep = 30
    // only this number of builds have their artifacts kept
    public int artifactNumToKeep = -1
    // history is only kept up to this days
    public int daysToKeep = -1
    // only this number of build logs are kept
    public int numToKeep = -1

    private static int ARTIFACTS_DAYS_TO_KEEP_MAX_VALUE = 30
    private static int ARTIFACTS_NUM_TO_KEEP_MAX_VALUE = 5
    private static int DAYS_TO_KEEP_MAX_VALUE = 10
    private static int NUM_TO_KEEP_MAX_VALUE = 10

    //endregion

    TagPipeline(Object script) {
        super(script)
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(TagPipeline ctx) {
        def script = ctx.script

        CommonUtil.printInitialStageStrategies(ctx)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        ctx.applyStrategiesFromParams(ctx)

        //если триггером был webhook параметры устанавливаются как env, если запустили вручную, то устанавливается как params
        extractValueFromEnvOrParamsAndRun(script, REPO_TAG_PARAMETER) {
            value -> ctx.repoTag = value
        }
        extractValueFromEnvOrParamsAndRun(script, TAG_HASH_PARAMETER) {
            value -> ctx.tagHash = value
        }

        def buildDescription = ctx.repoTag
        CommonUtil.setBuildDescription(script, buildDescription)
        CommonUtil.abortDuplicateBuildsWithDescription(script, AbortDuplicateStrategy.ANOTHER, buildDescription)
        RepositoryUtil.notifyGitlabAboutStagePending(ctx.script, ctx.repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE, ctx.tagHash)
    }

    def static checkoutStageBody(Object script, String url, String repoTag, String credentialsId) {
        script.git(
                url: url,
                credentialsId: credentialsId,
                poll: true
        )

        script.sh "git checkout tags/$repoTag"

        RepositoryUtil.checkLastCommitMessageContainsSkipCiLabel(script)

        RepositoryUtil.saveCurrentGitCommitHash(script)
    }

    def static prepareMessageForPipeline(TagPipeline ctx, Closure handler) {
        if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Завершена сборка по тэгу: ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlSlackLink(ctx.script)}"
            handler(message)
        }
    }

    def static versionPushStageBody(Object script,
                                    String repoTag,
                                    Collection<String> branchesPatternsForAutoChangeVersion,
                                    String repoUrl,
                                    String repoCredentialsId,
                                    String changeVersionCommitMessage) {
        //find branch for change version
        def branches = RepositoryUtil.getRefsForCurrentCommitMessage(script)
        def branchForChangeVersion = null
        for (branchRegexp in branchesPatternsForAutoChangeVersion) {
            Pattern pattern = Pattern.compile(branchRegexp)
            for (branch in branches) {
                if (pattern.matcher(branch).matches()) {
                    branchForChangeVersion = branch
                    break
                }
            }
            if (branchForChangeVersion) {
                break
            }
        }

        if (!branchForChangeVersion) {
            script.error "WARN: Do not find suitable branch for setting version. Branches searched for patterns: $branchesPatternsForAutoChangeVersion"
        }

        script.sh "git stash"

        def localBranch = branchForChangeVersion.replace("origin/", "")
        script.sh "git checkout -B $localBranch $branchForChangeVersion"

        script.sh "git stash apply"

        RepositoryUtil.setDefaultJenkinsGitUser(script)

        //commit and push new version
        script.sh "git commit -a -m \"$changeVersionCommitMessage\""
        RepositoryUtil.push(script, repoUrl, repoCredentialsId)

        //reset tag to new commit and push
        script.sh "git tag -f $repoTag"
        RepositoryUtil.pushForceTag(script, repoUrl, repoCredentialsId)
    }

    def static finalizeStageBody(TagPipeline ctx) {
        RepositoryUtil.notifyGitlabAboutStageFinish(ctx.script, ctx.repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE, ctx.jobResult, ctx.tagHash)
        if (ctx.getStage(ctx.CHECKOUT).result != Result.ABORTED) { //do not handle builds skipped via [skip ci] label
            JarvisUtil.createVersionAndNotify(ctx)
        }
    }

    def static debugFinalizeStageBody(TagPipeline ctx) {
        RepositoryUtil.notifyGitlabAboutStageFinish(ctx.script, ctx.repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE, ctx.jobResult, ctx.tagHash)
        if (ctx.getStage(ctx.CHECKOUT).result != Result.ABORTED) { //do not handle builds skipped via [skip ci] label
            JarvisUtil.createVersionAndNotify(ctx)
        }
        prepareMessageForPipeline(ctx, { message ->
            JarvisUtil.sendMessageToGroup(ctx.script, message, "9d0c617e-d14a-490e-9914-83820b135cfc", "stride", false)
        })
    }

    def static preExecuteStageBodyTag(Object script, SimpleStage stage, String repoUrl) {
        RepositoryUtil.notifyGitlabAboutStageStart(script, repoUrl, stage.name)
        RepositoryUtil.notifyGitlabAboutStageStart(script, repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE)
    }

    def static postExecuteStageBodyTag(Object script, SimpleStage stage, String repoUrl) {
        RepositoryUtil.notifyGitlabAboutStageFinish(script, repoUrl, stage.name, stage.result)
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String UNIT_TEST_STAGE_STRATEGY_PARAMETER = 'unitTestStageStrategy'
    public static final String INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER = 'instrumentationTestStageStrategy'
    public static final String STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER = 'staticCodeAnalysisStageStrategy'
    public static final String BETA_UPLOAD_STAGE_STRATEGY_PARAMETER = 'betaUploadStageStrategy'
    public static final String REPO_TAG_PARAMETER = 'repoTag_0'
    public static final String TAG_HASH_PARAMETER = 'tagHash'
    public static final String STAGE_STRATEGY_PARAM_DESCRIPTION = 'stage strategy types, see repo <a href="https://bitbucket.org/surfstudio/jenkins-pipeline-lib">jenkins-pipeline-lib</a> , class StageStrategy. If empty, job will use initial strategy for this stage'

    static List<Object> properties(TagPipeline ctx) {
        def script = ctx.script
        return [
                buildDiscarder(ctx, script),
                parameters(script),
                triggers(script, ctx.repoUrl, ctx.tagRegexp),
                script.gitLabConnection(ctx.gitlabConnection)
        ]
    }

    def static buildDiscarder(TagPipeline ctx, script) {
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
                [
                        $class       : 'GitParameterDefinition',
                        name         : REPO_TAG_PARAMETER,
                        type         : 'PT_TAG',
                        description  : 'Тег для сборки',
                        selectedValue: 'NONE',
                        sortMode     : 'DESCENDING_SMART'
                ],
                script.string(
                        name: UNIT_TEST_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),
                script.string(
                        name: INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),
                script.string(
                        name: STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),
                script.string(
                        name: BETA_UPLOAD_STAGE_STRATEGY_PARAMETER,
                        description: STAGE_STRATEGY_PARAM_DESCRIPTION),

        ])
    }

    def static triggers(script, String repoUrl, String tagRegexp) {
        return script.pipelineTriggers([
                script.GenericTrigger(
                        genericVariables: [
                                [
                                        key  : 'repoTag_0', //параметер tag будет доступен по ключу repoTag_0 - особенности GenericWebhookTrigger Plugin
                                        value: '$.ref',
                                        regexpFilter: 'refs/tags/'
                                ],
                                [
                                        key  : 'repoUrl',
                                        value: '$.project.web_url'
                                ],
                                [
                                        key :  TAG_HASH_PARAMETER,
                                        value: '$.checkout_sha'
                                ]
                        ],
                        printContributedVariables: true,
                        printPostContent: true,
                        causeString: 'Triggered by Gitlab',
                        regexpFilterExpression: /$repoUrl $tagRegexp/,
                        regexpFilterText: '$repoUrl $repoTag_0'
                ),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}
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
package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.base.LogRotatorUtil
import ru.surfstudio.ci.stage.SimpleStage
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.extractValueFromEnvOrParamsAndRun

abstract class PrPipeline extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = "Checkout"
    public static final String PRE_MERGE = 'PreMerge'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String CODE_STYLE_FORMATTING = 'Code Style Formatting'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'
    public static final String UPDATE_CURRENT_COMMIT_HASH_AFTER_FORMAT = "Update current commit hash after format"

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""
    public boolean targetBranchChanged = false

    //other config
    public stagesForTargetBranchChangedMode = [CHECKOUT, PRE_MERGE]

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

    PrPipeline(Object script) {
        super(script)
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(PrPipeline ctx) {
        initBodyWithOutAbortDuplicateBuilds(ctx)
        abortDuplicateBuildsWithDescription(ctx)
    }

    def static initBodyWithOutAbortDuplicateBuilds(PrPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)


        //если триггером был webhook параметры устанавливаются как env, если запустили вручную, то устанавливается как params
        extractValueFromEnvOrParamsAndRun(script, SOURCE_BRANCH_PARAMETER) {
            value -> ctx.sourceBranch = value
        }
        extractValueFromEnvOrParamsAndRun(script, DESTINATION_BRANCH_PARAMETER) {
            value -> ctx.destinationBranch = value
        }
        extractValueFromEnvOrParamsAndRun(script, AUTHOR_USERNAME_PARAMETER) {
            value -> ctx.authorUsername = value
        }
        extractValueFromEnvOrParamsAndRun(script, TARGET_BRANCH_CHANGED_PARAMETER) {
            value -> ctx.targetBranchChanged = Boolean.valueOf(value)
        }

        if (ctx.targetBranchChanged) {
            script.echo "Build triggered by target branch changes, run only ${ctx.stagesForTargetBranchChangedMode} stages"
            ctx.forStages { stage ->
                if (!(stage instanceof SimpleStage)) {
                    return
                }

                def executeStage = false
                for (stageNameForTargetBranchChangedMode in ctx.stagesForTargetBranchChangedMode) {
                    executeStage = executeStage || (stageNameForTargetBranchChangedMode == stage.getName())
                }
                if (!executeStage) {
                    stage.strategy = StageStrategy.SKIP_STAGE
                }
            }
        }

        CommonUtil.setBuildDescription(script, ctx.buildDescription())
    }

    def static abortDuplicateBuildsWithDescription(PrPipeline ctx) {
        CommonUtil.abortDuplicateBuildsWithDescription(ctx.script, AbortDuplicateStrategy.ANOTHER, ctx.buildDescription())
    }

    def static preMergeStageBody(Object script, String url, String sourceBranch, String destinationBranch, String credentialsId) {
        checkout(script, url, sourceBranch, credentialsId)
        mergeLocal(script, destinationBranch)
        saveCommitHashAndCheckSkipCi(script, false)
    }

    def static checkout(Object script, String url, String sourceBranch, String credentialsId) {
        //script.sh 'git config --global user.name "Jenkins"'
        //script.sh 'git config --global user.email "jenkins@surfstudio.ru"'

        CommonUtil.safe(script) {
            script.sh "git reset --merge" //revert previous failed merge
            RepositoryUtil.revertUncommittedChanges(script)
        }

        script.git(
                url: url,
                credentialsId: credentialsId,
                branch: sourceBranch
        )
    }

    def static mergeLocal(Object script, String destinationBranch) {
        //local merge with destination
        script.sh "git merge origin/$destinationBranch --no-ff --no-commit"
    }

    def static saveCommitHashAndCheckSkipCi(Object script, boolean targetBranchChanged) {
        RepositoryUtil.saveCurrentGitCommitHash(script)
        if (!targetBranchChanged) {
            RepositoryUtil.checkLastCommitMessageContainsSkipCiLabel(script)
        }
    }

    def static prepareMessageForPipeline(PrPipeline ctx, Closure handler) {
        if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED && ctx.jobResult != Result.NOT_BUILT) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Ветка ${ctx.sourceBranch} в состоянии ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlSlackLink(ctx.script)}"
            handler(message)
        }
    }

    def static finalizeStageBody(PrPipeline ctx) {
        prepareMessageForPipeline(ctx, { message ->
            JarvisUtil.sendMessageToUser(ctx.script, message, ctx.authorUsername, "bitbucket")
        })
    }

    def static debugFinalizeStageBody(PrPipeline ctx) {
        prepareMessageForPipeline(ctx, { message ->
            JarvisUtil.sendMessageToUser(ctx.script, message, ctx.authorUsername, "bitbucket")
            JarvisUtil.sendMessageToGroup(ctx.script, message, "9d0c617e-d14a-490e-9914-83820b135cfc", "stride", false)
        })
    }

    def static preExecuteStageBodyPr(Object script, SimpleStage stage, String repoUrl) {
        RepositoryUtil.notifyBitbucketAboutStageStart(script, repoUrl, stage.name)
    }

    def static postExecuteStageBodyPr(Object script, SimpleStage stage, String repoUrl) {
        RepositoryUtil.notifyBitbucketAboutStageFinish(script, repoUrl, stage.name, stage.result)
    }

    String buildDescription() {
        return targetBranchChanged ?
                "$sourceBranch to $destinationBranch: target branch changed" :
                "$sourceBranch to $destinationBranch"
    }

    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String SOURCE_BRANCH_PARAMETER = 'sourceBranch'
    public static final String DESTINATION_BRANCH_PARAMETER = 'destinationBranch'
    public static final String AUTHOR_USERNAME_PARAMETER = 'authorUsername'
    public static final String TARGET_BRANCH_CHANGED_PARAMETER = 'targetBranchChanged'

    static List<Object> properties(PrPipeline ctx) {
        def script = ctx.script
        return [
                buildDiscarder(ctx, script),
                parameters(script),
                triggers(script, ctx.repoUrl)
        ]
    }

    def static buildDiscarder(PrPipeline ctx, script) {
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
                        description: 'Ветка с pr, обязательный параметр'),
                script.string(
                        name: DESTINATION_BRANCH_PARAMETER,
                        description: 'Ветка, в которую будет мержиться пр, обязательный параметр'),
                script.string(
                        name: AUTHOR_USERNAME_PARAMETER,
                        description: 'username в bitbucket создателя пр, нужно для отправки собщений, обязательный параметр')
        ])
    }

    def static triggers(script, String repoUrl) {
        return script.pipelineTriggers([
                script.GenericTrigger(
                        genericVariables: [
                                [
                                        key  : SOURCE_BRANCH_PARAMETER,
                                        value: '$.pullrequest.source.branch.name'
                                ],
                                [
                                        key  : DESTINATION_BRANCH_PARAMETER,
                                        value: '$.pullrequest.destination.branch.name'
                                ],
                                [
                                        key  : AUTHOR_USERNAME_PARAMETER,
                                        value: '$.pullrequest.author.account_id'
                                ],
                                [
                                        key  : 'repoUrl',
                                        value: '$.repository.links.html.href'
                                ],
                                [
                                        key  : TARGET_BRANCH_CHANGED_PARAMETER,
                                        value: '$.target_branch.changed' //параметер добавляет jarvis
                                ]
                        ],
                        printContributedVariables: true,
                        printPostContent: true,
                        causeString: 'Triggered by Bitbucket',
                        regexpFilterExpression: '^' + "$repoUrl" + '$',
                        regexpFilterText: '$repoUrl'
                ),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}

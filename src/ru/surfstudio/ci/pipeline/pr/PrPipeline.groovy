package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.ScmAutoAbortedPipeline
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

abstract class PrPipeline extends ScmAutoAbortedPipeline {

    //stage names
    public static final String PRE_MERGE = 'PreMerge'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""
    public boolean targetBranchChanged = false

    //other config
    public stagesForTargetBranchChangedMode = [PRE_MERGE]


    PrPipeline(Object script) {
        super(script)
    }

    @Override
    String getBuildIdentifier() {
        if (targetBranchChanged) {
            return "$sourceBranch: target branch changed"
        } else {
            return sourceBranch
        }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initStageBody(PrPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)

        def params = script.params

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        applyParameterIfNotEmpty(script, SOURCE_BRANCH_PARAMETER, params[SOURCE_BRANCH_PARAMETER], {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(script, DESTINATION_BRANCH_PARAMETER, params.destinationBranch, {
            value -> ctx.destinationBranch = value
        })
        applyParameterIfNotEmpty(script, AUTHOR_USERNAME_PARAMETER, params.authorUsername, {
            value -> ctx.authorUsername = value
        })

        applyParameterIfNotEmpty(script, TARGET_BRANCH_CHANGED_PARAMETER, params.targetBranchChanged, {
            value -> ctx.targetBranchChanged = Boolean.valueOf(value)
        })

        if(ctx.targetBranchChanged) {
            script.echo "Build triggered by target branch changes, run only ${ctx.stagesForTargetBranchChangedMode} stages"
            for (stage in ctx.stages) {
                def executeStage = false
                for(stageNameForTargetBranchChangedMode in ctx.stagesForTargetBranchChangedMode){
                    executeStage = executeStage || (stageNameForTargetBranchChangedMode == stage.getName())
                }
                if(!executeStage) {
                    stage.strategy = StageStrategy.SKIP_STAGE
                }
            }
        }
    }

    def static preMergeStageBody(Object script, String url, String sourceBranch, String destinationBranch, String credentialsId) {
        script.sh 'git config --global user.name "Jenkins"'
        script.sh 'git config --global user.email "jenkins@surfstudio.ru"'

        script.git(
                url: url,
                credentialsId: credentialsId,
                branch: sourceBranch
        )

        RepositoryUtil.saveCurrentGitCommitHash(script)

        //local merge with destination
        script.sh "git merge origin/$destinationBranch --no-ff"
    }

    def static prepareMessageForPipeline(PrPipeline ctx, Closure handler) {
        if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Ветка ${ctx.sourceBranch} в состоянии ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlMarkdownLink(ctx.script)}"
            handler(message)
        }
    }

    def static finalizeStageBody(PrPipeline ctx){
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

    def static Closure<Object> getPostExecuteStageBody() {
        { stage ->
            if (stage.name != PRE_MERGE) RepositoryUtil.notifyBitbucketAboutStageFinish(script, repoUrl, stage.name, stage.result)
        }
    }

    def static Closure<Object> getPreExecuteStageBody() {
        { stage ->
            if (stage.name != PRE_MERGE) RepositoryUtil.notifyBitbucketAboutStageStart(script, repoUrl, stage.name)
        }
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String SOURCE_BRANCH_PARAMETER = 'sourceBranch'
    public static final String DESTINATION_BRANCH_PARAMETER = 'destinationBranch'
    public static final String AUTHOR_USERNAME_PARAMETER = 'authorUsername'
    public static final String TARGET_BRANCH_CHANGED_PARAMETER = 'targetBranchChanged'

    def static List<Object> properties(PrPipeline ctx) {
        def script = ctx.script
        return [
                buildDiscarder(script),
                parameters(script),
                triggers(script, ctx.repoUrl)
        ]
    }

    def static buildDiscarder(script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: '3',
                        artifactNumToKeepStr: '10',
                        daysToKeepStr: '180',
                        numToKeepStr: '')
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
                        description: 'username в bitbucket создателя пр, нужно для отправки собщений, обязательный параметр'),
                script.booleanParam(
                        name: TARGET_BRANCH_CHANGED_PARAMETER,
                        defaultValue: false,
                        description: 'Не следует указывать, параметр нужен здесь для пробрасывания его в клон билда')
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
                                        value: '$.pullrequest.author.username'
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
                        regexpFilterExpression: "$repoUrl",
                        regexpFilterText: '$repoUrl'
                ),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}
package ru.surfstudio.ci.stage.body
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.PrPipeline
import ru.surfstudio.ci.pipeline.PrPipelineAndroid
import ru.surfstudio.ci.stage.StageStrategy

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

class PrStages {


    public static final String SOURCE_BRANCH_PARAMETER_NAME = 'sourceBranch'
    public static final String DESTINATION_BRANCH_PARAMETER_NAME = 'destinationBranch'
    public static final String AUTHOR_USERNAME_PARAMETER_NAME = 'authorUsername'
    public static final String TARGET_BRANCH_CHANGED = 'targetBranchChanged'

    def static Closure<List<Object>> propertiesProvider(PrPipeline ctx) {
        return { properties(ctx) }
    }
    def static List<Object> properties(PrPipeline ctx) {
        def script = ctx.script
        CommonUtil.checkConfigurationParameterDefined(script, ctx.repoFullName, "repoFullName")
        return [
                //[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
                //[$class: 'JobRestrictionProperty'],
                script.buildDiscarder(
                        script.logRotator(
                                artifactDaysToKeepStr: '3',
                                artifactNumToKeepStr: '10',
                                daysToKeepStr: '180',
                                numToKeepStr: '')
                ),
                script.parameters([
                        script.string(
                                name: SOURCE_BRANCH_PARAMETER_NAME,
                                description: 'Ветка с pr, обязательный параметр'),
                        script.string(
                                name: DESTINATION_BRANCH_PARAMETER_NAME,
                                description: 'Ветка, в которую будет мержиться пр, обязательный параметр'),
                        script.string(
                                name: AUTHOR_USERNAME_PARAMETER_NAME,
                                description: 'username в bitbucket создателя пр, нужно для отправки собщений, обязательный параметр'),
                        script.booleanParam(
                                name: TARGET_BRANCH_CHANGED,
                                defaultValue: false,
                                description: 'Не следует указывать, параметр нужен здесь для пробрасывания его в клон билда')
                ]),
                script.pipelineTriggers([
                        script.GenericTrigger(
                                genericVariables: [
                                        [
                                                key  : SOURCE_BRANCH_PARAMETER_NAME,
                                                value: '$.pullrequest.source.branch.name'
                                        ],
                                        [
                                                key  : DESTINATION_BRANCH_PARAMETER_NAME,
                                                value: '$.pullrequest.destination.branch.name'
                                        ],
                                        [
                                                key  : AUTHOR_USERNAME_PARAMETER_NAME,
                                                value: '$.pullrequest.author.username'
                                        ],
                                        [
                                                key  : 'repoFullName',
                                                value: '$.repository.full_name'
                                        ],
                                        [
                                                key  : TARGET_BRANCH_CHANGED,
                                                value: '$.target_branch.changed'
                                        ]
                                ],
                                printContributedVariables: true,
                                printPostContent: true,
                                causeString: 'Triggered by Bitbucket',
                                //regexpFilterExpression: "$ctx.repoFullName",
                                //regexpFilterText: '$repoFullName'
                        ),
                        script.pollSCM('')
                ])

        ]
    }

    def static initStageBody(PrPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)
        
        def params = script.params

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        applyParameterIfNotEmpty(script, SOURCE_BRANCH_PARAMETER_NAME, params[SOURCE_BRANCH_PARAMETER_NAME], {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(script, DESTINATION_BRANCH_PARAMETER_NAME, params.destinationBranch, {
            value -> ctx.destinationBranch = value
        })
        applyParameterIfNotEmpty(script, AUTHOR_USERNAME_PARAMETER_NAME, params.authorUsername, {
            value -> ctx.authorUsername = value
        })

        applyParameterIfNotEmpty(script, TARGET_BRANCH_CHANGED, params.targetBranchChanged, {
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

    def static preMergeStageBody(Object script, String sourceBranch, String destinationBranch) {
        script.sh 'git config --global user.name "Jenkins"'
        script.sh 'git config --global user.email "jenkins@surfstudio.ru"'
        script.checkout changelog: true, poll: true, scm:
                [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${sourceBranch}"]],
                        doGenerateSubmoduleConfigurations: false,
                        userRemoteConfigs                : script.scm.userRemoteConfigs
                ]

        RepositoryUtil.saveCurrentGitCommitHash(script)

        script.checkout changelog: true, poll: true, scm:
                [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${sourceBranch}"]],
                        doGenerateSubmoduleConfigurations: false,
                        userRemoteConfigs                : script.scm.userRemoteConfigs,
                        extensions                       : [
                                [
                                        $class : 'PreBuildMerge',
                                        options: [
                                                mergeStrategy  : 'DEFAULT',
                                                fastForwardMode: 'NO_FF',
                                                mergeRemote    : 'origin',
                                                mergeTarget    : "${destinationBranch}"
                                        ]
                                ]
                        ]
                ]
    }

    def static prepareMessageForPipeline(PrPipeline ctx, Closure handler) {
        if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Ветка ${ctx.sourceBranch} в состоянии ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlMarkdownLink(ctx.script)}"
            handler(message)
        }
    }

    def static finalizeStageBody(PrPipeline ctx){
        PrStages.prepareMessageForPipeline(ctx, { message ->
            JarvisUtil.sendMessageToUser(ctx.script, message, ctx.authorUsername, "bitbucket")
        })
    }

    def static debugFinalizeStageBody(PrPipeline ctx) {
        PrStages.prepareMessageForPipeline(ctx, { message ->
            JarvisUtil.sendMessageToUser(ctx.script, message, ctx.authorUsername, "bitbucket")
            JarvisUtil.sendMessageToGroup(ctx.script, message, "9d0c617e-d14a-490e-9914-83820b135cfc", "stride", false) 
        })
    }
}
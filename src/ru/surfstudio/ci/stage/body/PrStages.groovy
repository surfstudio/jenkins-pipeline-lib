package ru.surfstudio.ci.stage.body
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.PrPipeline

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

class PrStages {

    def static initStageBody(PrPipeline ctx) {
        def script = ctx.script
        CommonUtil.printInitialStageStrategies(ctx)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        //Параметры могут быть установлены только если Job стартовали вручную
        def params = script.params
        CommonUtil.applyStrategiesFromParams(ctx, [
                (ctx.PRE_MERGE): params.preMergeStageStrategy,
                (ctx.BUILD): params.buildStageStrategy,
                (ctx.UNIT_TEST): params.unitTestStageStrategy,
                (ctx.INSTRUMENTATION_TEST): params.instrumentationTestStageStrategy,
                (ctx.STATIC_CODE_ANALYSIS): params.staticCodeAnalysisStageStrategy
        ])

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        applyParameterIfNotEmpty(script, 'sourceBranch', params.sourceBranch, {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(script, 'destinationBranch', params.destinationBranch, {
            value -> ctx.destinationBranch = value
        })
        applyParameterIfNotEmpty(script, 'authorUsername', params.authorUsername, {
            value -> ctx.authorUsername = value
        })
        script.echo "envdest: ${script.env.destinationBranch}"
        script.echo "params: ${params.targetBranchChanged}"
        script.echo "env: ${script.env.targetBranchChanged}"
        applyParameterIfNotEmpty(script, 'targetBranchChanged', params.targetBranchChanged, {
            value -> ctx.targetBranchChanged = value
        })
        if(ctx.targetBranchChanged) {
            script.echo "build triggered by target branch changes"
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
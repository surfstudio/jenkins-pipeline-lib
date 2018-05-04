package ru.surfstudio.ci.stage.body
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.PrPipeline

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty
import static ru.surfstudio.ci.CommonUtil.printDefaultVar

class PrStages {

    def static initStageBody(PrPipeline ctx) {
        def script = ctx.script
        Error
        printDefaultVar(script, 'preMergeStageStrategy', ctx.preMergeStageStrategy)
        printDefaultVar(script, 'buildStageStrategy', ctx.buildStageStrategy)
        printDefaultVar(script, 'unitTestStageStrategy', ctx.unitTestStageStrategy)
        printDefaultVar(script, 'smallInstrumentationTestStageStrategy', ctx.smallInstrumentationTestStageStrategy)
        printDefaultVar(script, 'staticCodeAnalysisStageStrategy', ctx.staticCodeAnalysisStageStrategy)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        //Параметры могут быть установлены только если Job стартовали вручную
        applyParameterIfNotEmpty(script, 'preMergeStageStrategy', script.params.preMergeStageStrategy) {
            param -> ctx.preMergeStageStrategy = param
        }
        applyParameterIfNotEmpty(script, 'buildStageStrategy', script.params.buildStageStrategy) {
            param -> ctx.buildStageStrategy = param
        }
        applyParameterIfNotEmpty(script, 'unitTestStageStrategy', script.params.unitTestStageStrategy) {
            param -> ctx.unitTestStageStrategy = param
        }
        applyParameterIfNotEmpty(script, 'smallInstrumentationTestStageStrategy', script.params.smallInstrumentationTestStageStrategy) {
            param -> ctx.smallInstrumentationTestStageStrategy = param
        }
        applyParameterIfNotEmpty(script, 'staticCodeAnalysisStageStrategy', script.params.staticCodeAnalysisStageStrategy) {
            param -> ctx.staticCodeAnalysisStageStrategy = param
        }

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        applyParameterIfNotEmpty(script, 'sourceBranch', script.params.sourceBranch, {
            value -> ctx.sourceBranch = value
        })
        applyParameterIfNotEmpty(script, 'destinationBranch', script.params.destinationBranch, {
            value -> ctx.destinationBranch = value
        })
        applyParameterIfNotEmpty(script, 'authorUsername', script.params.authorUsername, {
            value -> ctx.authorUsername = value
        })

        CommonUtil.abortDuplicateBuilds(script, ctx.sourceBranch)
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
                                                mergeStrategy  : 'MergeCommand.Strategy',
                                                fastForwardMode: 'NO_FF',
                                                mergeRemote    : 'origin',
                                                mergeTarget    : "${destinationBranch}"
                                        ]
                                ]
                        ]
                ]
        script.echo 'PreMerge Success'
    }

    def static finalizeStageBody(PrPipeline ctx){
        if (ctx.jobResult != Result.SUCCESS) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx)
            def message = "Ветка ${ctx.sourceBranch} в состоянии ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlHtmlLink(ctx.script)}"
            JarvisUtil.sendMessageToUser(ctx.script, message, ctx.authorUsername, "bitbucket")
        }
    }


}
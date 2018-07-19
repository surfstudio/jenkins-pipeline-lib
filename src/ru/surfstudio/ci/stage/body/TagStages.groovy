package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.AndroidUtil
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.pipeline.TagPipeline

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

class TagStages {

    def static initStageBody(TagPipeline ctx) {
        def script = ctx.script

        CommonUtil.printInitialStageStrategies(ctx)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        //Параметры могут быть установлены только если Job стартовали вручную
        def params = script.params
        CommonUtil.applyStrategiesFromParams(ctx, [
                (ctx.CHECKOUT): params.checkoutStageStrategy,
                (ctx.BUILD): params.buildStageStrategy,
                (ctx.UNIT_TEST): params.unitTestStageStrategy,
                (ctx.INSTRUMENTATION_TEST): params.instrumentationTestStageStrategy,
                (ctx.STATIC_CODE_ANALYSIS): params.staticCodeAnalysisStageStrategy,
                (ctx.BETA_UPLOAD): params.betaUploadStageStrategy,
        ])

        //Выбираем значения веток и автора из параметров, Установка их в параметры происходит
        // если триггером был webhook или если стартанули Job вручную
        //Используется имя repoTag_0 из за особенностей jsonPath в GenericWebhook plugin
        applyParameterIfNotEmpty(script,'repoTag', script.params.repoTag_0, {
            value -> ctx.repoTag = value
        })

        CommonUtil.abortDuplicateBuilds(script, ctx.repoTag)
    }

    def static checkoutStageBody(Object script, String repoTag) {
        script.checkout([
                $class                           : 'GitSCM',
                branches                         : [[name: "refs/tags/${repoTag}"]],
                doGenerateSubmoduleConfigurations: script.scm.doGenerateSubmoduleConfigurations,
                userRemoteConfigs                : script.scm.userRemoteConfigs,
        ])
    }

    def static betaUploadStageBodyAndroid(Object script, String betaUploadGradleTask) {
        script.sh "./gradlew ${betaUploadGradleTask}"
    }

    def static betaUploadWithKeystoreStageBodyAndroid(Object script,
                                                      String betaUploadGradleTask,
                                                      String keystoreCredentials,
                                                      String keystorePropertiesCredentials) {
        AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials) {
                betaUploadStageBodyAndroid(script, betaUploadGradleTask)
        }
    }

    def static betaUploadStageBodyiOS(Object script, String keychainCredenialId, String certfileCredentialId) {
        script.withCredentials([
            script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
            script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
        ]) {

            script.sh 'security -v unlock-keychain -p $KEYCHAIN_PASS'
            script.sh 'security import "$DEVELOPER_P12_KEY" -P ""'
            
            script.sh "make init"
            script.sh "make beta"
        }
    }

    def static finalizeStageBody(TagPipeline ctx) {
        JarvisUtil.createVersionAndNotify(ctx)
    }
    
}
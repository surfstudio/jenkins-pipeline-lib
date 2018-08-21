package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.AbortDuplicateStrategy
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.pipeline.pr.PrPipeline

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

abstract class TagPipeline extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = 'Checkout'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'
    public static final String BETA_UPLOAD = 'Beta Upload'

    //scm
    public repoTag = ""

    TagPipeline(Object script) {
        super(script)
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(TagPipeline ctx) {
        def script = ctx.script

        CommonUtil.printInitialStageStrategies(ctx)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        def params = script.params
        CommonUtil.applyStrategiesFromParams(ctx, [
                (ctx.UNIT_TEST): params[UNIT_TEST_STAGE_STRATEGY_PARAMETER],
                (ctx.INSTRUMENTATION_TEST): params[INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER],
                (ctx.STATIC_CODE_ANALYSIS): params[STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER],
                (ctx.BETA_UPLOAD): params[BETA_UPLOAD_STAGE_STRATEGY_PARAMETER],
        ])

        applyParameterIfNotEmpty(script,'repoTag', params[REPO_TAG_PARAMETER], {
            value -> ctx.repoTag = value
        })

        def buildDescription = ctx.repoTag
        CommonUtil.setBuildDescription(script, buildDescription)
        CommonUtil.abortDuplicateBuildsWithDescription(script, AbortDuplicateStrategy.ANOTHER, buildDescription)
    }

    def static checkoutStageBody(Object script,  String url, String repoTag, String credentialsId) {
        script.git(
                url: url,
                credentialsId: credentialsId
        )
        script.sh "git checkout tags/$repoTag"
        RepositoryUtil.saveCurrentGitCommitHash(script)
    }

    def static prepareMessageForPipeline(TagPipeline ctx, Closure handler) {
        if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Завершена сборка по тэгу: ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlMarkdownLink(ctx.script)}"
            handler(message)
        }
    }

    def static finalizeStageBody(TagPipeline ctx){
        JarvisUtil.createVersionAndNotify(ctx)
    }

    def static debugFinalizeStageBody(TagPipeline ctx) {
        JarvisUtil.createVersionAndNotify(ctx)
        prepareMessageForPipeline(ctx, { message ->
            JarvisUtil.sendMessageToGroup(ctx.script, message, "9d0c617e-d14a-490e-9914-83820b135cfc", "stride", false)
        })
    }

    def static Closure<Object> getPostExecuteStageBody(Object script, String repoUrl) {
        { stage ->
            if (stage.name != CHECKOUT) RepositoryUtil.notifyBitbucketAboutStageFinish(script, repoUrl, stage.name, stage.result)
        }
    }

    def static Closure<Object> getPreExecuteStageBody(Object script, String repoUrl) {
        { stage ->
            if (stage.name != CHECKOUT) RepositoryUtil.notifyBitbucketAboutStageStart(script, repoUrl, stage.name)
        }
    }
    // =============================================== 	↑↑↑  END EXECUTION LOGIC ↑↑↑ =================================================


    // ============================================= ↓↓↓ JOB PROPERTIES CONFIGURATION ↓↓↓  ==========================================

    //parameters
    public static final String UNIT_TEST_STAGE_STRATEGY_PARAMETER = 'unitTestStageStrategy'
    public static final String INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER = 'instrumentationTestStageStrategy'
    public static final String STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER = 'staticCodeAnalysisStageStrategy'
    public static final String BETA_UPLOAD_STAGE_STRATEGY_PARAMETER = 'betaUploadStageStrategy'
    public static final String REPO_TAG_PARAMETER = 'repoTag_0'

    public static final String STAGE_STRATEGY_PARAM_DESCRIPTION = 'stage strategy types, see repo <a href="https://bitbucket.org/surfstudio/jenkins-pipeline-lib">jenkins-pipeline-lib</a> , class StageStrategy. If empty, job will use initial strategy for this stage'

    def static List<Object> properties(TagPipeline ctx) {
        def script = ctx.script
        return [
                buildDiscarder(script),
                parameters(script),
                triggers(script, ctx.repoUrl)
        ]
    }

    private static void buildDiscarder(script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: '90',
                        artifactNumToKeepStr: '',
                        daysToKeepStr: '',
                        numToKeepStr: '')
        )
    }

    private static void parameters(script) {
        return script.parameters([
                [
                        $class     : 'GitParameterDefinition',
                        name       : REPO_TAG_PARAMETER,
                        type       : 'PT_TAG',
                        description: 'Тег для сборки',
                        selectedValue: 'NONE',
                        sortMode: 'DESCENDING_SMART'
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

    private static void triggers(script, String repoUrl) {
        return script.pipelineTriggers([
                script.GenericTrigger(
                        genericVariables: [
                                [
                                        key  : 'repoTag', //параметер tag будет доступен по ключу repoTag_0 - особенности GenericWebhookTrigger Plugin
                                        value: '$.push.changes[?(@.new.type == "tag")].new.name'
                                ],
                                [
                                        key  : 'repoUrl',
                                        value: '$.repository.links.html.href'
                                ]
                        ],
                        printContributedVariables: true,
                        printPostContent: true,
                        causeString: 'Triggered by Bitbucket',
                        regexpFilterExpression: /$repoUrl (.*)?(v)?\d{1,4}\.\d{1,4}\.\d{1,4}(\-rc\d{1,4})?/,
                        regexpFilterText: '$repoUrl $repoTag_0'
                ),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}
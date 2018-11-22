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
import ru.surfstudio.ci.AndroidUtil
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.error.UnstableStateThrowable
import ru.surfstudio.ci.pipeline.ScmPipeline
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageStrategy
import java.util.regex.Pattern
import java.util.regex.Matcher

import static ru.surfstudio.ci.CommonUtil.extractValueFromEnvOrParamsAndRun

abstract class TagPipeline extends ScmPipeline {

    //stage names
    public static final String CHECKOUT = 'Checkout'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'
    public static final String VERSION_UPDATE = 'Version Increase'
    public static final String BETA_UPLOAD = 'Beta Upload'
    public static final String VERSION_PUSH = 'Version Push'

    //scm
    public tagRegexp = /(.*)?\d{1,4}\.\d{1,4}\.\d{1,4}(.*)?/
    public repoTag = ""
    public setVersionFromTag = true
    public branchesPatternsForAutoSetVersion = [/^origin\/dev\/.*/, /^origin\/feature\/.*/]

    TagPipeline(Object script) {
        super(script)
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static initBody(TagPipeline ctx) {
        def script = ctx.script

        ctx.getStage(VERSION_UPDATE).strategy = ctx.setVersionFromTag ? StageStrategy.FAIL_WHEN_STAGE_ERROR : StageStrategy.SKIP_STAGE
        ctx.getStage(VERSION_PUSH).strategy = ctx.setVersionFromTag ? StageStrategy.UNSTABLE_WHEN_STAGE_ERROR : StageStrategy.SKIP_STAGE

        CommonUtil.printInitialStageStrategies(ctx)

        //Используем нестандартные стратегии для Stage из параметров, если они установлены
        def params = script.params
        def env = script.env
        CommonUtil.applyStrategiesFromParams(ctx, [
                (ctx.UNIT_TEST): params[UNIT_TEST_STAGE_STRATEGY_PARAMETER],
                (ctx.INSTRUMENTATION_TEST): params[INSTRUMENTATION_TEST_STAGE_STRATEGY_PARAMETER],
                (ctx.STATIC_CODE_ANALYSIS): params[STATIC_CODE_ANALYSIS_STAGE_STRATEGY_PARAMETER],
                (ctx.BETA_UPLOAD): params[BETA_UPLOAD_STAGE_STRATEGY_PARAMETER],
        ])

        //если триггером был webhook параметры устанавливаются как env, если запустили вручную, то устанавливается как params
        extractValueFromEnvOrParamsAndRun(script, REPO_TAG_PARAMETER) {
            value -> ctx.repoTag = value
        }

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

        RepositoryUtil.checkLastCommitMessageContainsSkipCiLabel(script)

        RepositoryUtil.saveCurrentGitCommitHash(script)
    }

    def static prepareMessageForPipeline(TagPipeline ctx, Closure handler) {
        if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED) {
            def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
            def message = "Завершена сборка по тэгу: ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlMarkdownLink(ctx.script)}"
            handler(message)
        }
    }

    def static versionPushStageBody(Object script,
                                    String repoTag,
                                    String gradleConfigFile,
                                    String appVersionNameGradleVar,
                                    String appVersionCodeGradleVar,
                                    String[] branchesPatternsForAutoSetVersion,
                                    String repoUrl,
                                    String repoCredentialsId) {
        //find branch for set version
        def branches = RepositoryUtil.getRefsForCurrentCommitMessage(script)
        def branchForSetVersion = null
        for (branchRegexp in branchesPatternsForAutoSetVersion) {
            Pattern pattern = Pattern.compile(branchRegexp)
            for(branch in branches){
                if (pattern.matcher(branch).matches()){
                    branchForSetVersion = branch
                    break
                }
            }
            if (branchForSetVersion) {
                break
            }
        }

        if(!branchForSetVersion){
            script.echo "WARN: Do not find suitable branch for set version. It serched for branches: $branchesPatternsForAutoSetVersion"
            throw new UnstableStateThrowable()
        }
        script.sh "git checkout -B $branchForSetVersion"

        //commit and push new version
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionNameGradleVar))
        def versionCode = AndroidUtil.getGradleVariable(script, gradleConfigFile, appVersionCodeGradleVar)
        script.sh "git commit -a -m \"Set version to $versionName ($versionCode) $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1\""
        RepositoryUtil.push(script, repoUrl, repoCredentialsId)

        //reset tag to new commit and push
        script.sh "git tag -f $repoTag"
        RepositoryUtil.pushForceTag(script, repoUrl, repoCredentialsId)
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

    def static preExecuteStageBodyTag(Object script, Stage stage, String repoUrl) {
        if (stage.name != CHECKOUT) RepositoryUtil.notifyBitbucketAboutStageStart(script, repoUrl, stage.name)
    }

    def static postExecuteStageBodyTag(Object script, Stage stage, String repoUrl) {
        if (stage.name != CHECKOUT) RepositoryUtil.notifyBitbucketAboutStageFinish(script, repoUrl, stage.name, stage.result)
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
                triggers(script, ctx.repoUrl, ctx.tagRegexp)
        ]
    }

    def static buildDiscarder(script) {
        return script.buildDiscarder(
                script.logRotator(
                        artifactDaysToKeepStr: '90',
                        artifactNumToKeepStr: '',
                        daysToKeepStr: '',
                        numToKeepStr: '')
        )
    }

    def static parameters(script) {
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

    def static triggers(script, String repoUrl, String tagRegexp) {
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
                        regexpFilterExpression: /$repoUrl $tagRegexp/,
                        regexpFilterText: '$repoUrl $repoTag_0'
                ),
                script.pollSCM('')
        ])
    }

    // ============================================= ↑↑↑  END JOB PROPERTIES CONFIGURATION ↑↑↑  ==========================================

}
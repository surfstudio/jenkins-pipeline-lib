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
package ru.surfstudio.ci

import ru.surfstudio.ci.pipeline.Pipeline
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageWithResult
import ru.surfstudio.ci.stage.StageWithStrategy
import ru.surfstudio.ci.utils.StageTreeUtil

class CommonUtil {

    static int MAX_DEPTH_FOR_SEARCH_SAME_BUILDS = 50
    static String EMPTY_STRING = ""

    def static getBuildUrlHtmlLink(Object script){
        return  "<a href=\"${script.env.JENKINS_URL}blue/organizations/jenkins/${script.env.JOB_NAME}/detail/${script.env.JOB_NAME}/${script.env.BUILD_NUMBER}/pipeline\">build</a>"
    }

    def static getJiraTaskHtmlLink(String taskKey){
        return "<a href=\"${Constants.JIRA_URL}browse/${taskKey}\">${taskKey}</a>"
    }

    def static getBuildUrlMarkdownLink(Object script){
        return  "[build](${script.env.JENKINS_URL}blue/organizations/jenkins/${script.env.JOB_NAME}/detail/${script.env.JOB_NAME}/${script.env.BUILD_NUMBER}/pipeline)"
    }

    def static getJiraTaskMarkdownLink(String taskKey){
        return "[${taskKey}](${Constants.JIRA_URL}browse/${taskKey})"
    }

    /**
     * Функция, проверяющая, что строка, переданная параметром, не равна null и не является пустой
     */
    static Boolean isNotNullOrEmpty(String string) {
        return string != null && string != EMPTY_STRING
    }

    /**
     * Функция, проверяющая, что строка, переданная параметром, равна null или является пустой
     */
    static Boolean isNullOrEmpty(String string) {
        return string == null || string == EMPTY_STRING
    }

    //region Environment variables
    static String getAndroidHome(Object script) {
        return script.env.ANDROID_HOME
    }

    static String getAdbHome(Object script) {
        return "${getAndroidHome(script)}/platform-tools/adb"
    }

    static String getEmulatorHome(Object script) {
        return "${getAndroidHome(script)}/emulator/emulator"
    }

    private static String getAndroidToolsHome(Object script) {
        return "${getAndroidHome(script)}/tools/bin"
    }

    static String getAaptHome(Object script, String buildToolsVersion) {
        return "${getAndroidHome(script)}/build-tools/$buildToolsVersion/aapt"
    }

    static String getAvdManagerHome(Object script) {
        return "${getAndroidToolsHome(script)}/avdmanager"
    }

    static String getSdkManagerHome(Object script) {
        return "${getAndroidToolsHome(script)}/sdkmanager"
    }
    //endregion

    def static shWithRuby(Object script, String command, String version = "2.5.5") {
        script.sh "hostname; set +x; source ~/.bashrc; source ~/.rvm/scripts/rvm; rvm use 2.5.5@flutter --create; ls -la; ${command}"
    }

    @Deprecated
    def static abortDuplicateBuilds(Object script, String buildIdentifier) {
        script.currentBuild.rawBuild.setDescription(buildIdentifier)
        tryAbortOlderBuildsWithDescription(script, buildIdentifier)
    }

    def static setBuildDescription(Object script, String description){
        script.currentBuild.rawBuild.setDescription(description)
    }

    def static abortDuplicateBuildsWithDescription(Object script, String abortStrategy, String buildDescription) {
        switch (abortStrategy){
            case AbortDuplicateStrategy.SELF:
                if(isOlderBuildWithDescriptionRunning(script, buildDescription)){
                    script.echo "Aborting current build..."
                    throw new InterruptedException("Another build with identical description '$buildDescription' is running")
                }
                break
            case AbortDuplicateStrategy.ANOTHER:
                tryAbortOlderBuildsWithDescription(script, buildDescription)
                break
            default:
                script.error("Unsupported AbortDuplicateStrategy: $abortStrategy")
        }
    }

    def static tryAbortOlderBuildsWithDescription(Object script, String buildDescription) {
        int depth = 0
        hudson.model.Run currentBuild = script.currentBuild.rawBuild
        hudson.model.Run previousBuild = currentBuild.getPreviousBuildInProgress()

        while (previousBuild != null && depth <= MAX_DEPTH_FOR_SEARCH_SAME_BUILDS) {
            depth++
            if (previousBuild.isInProgress() && previousBuild.getDescription() == buildDescription) {
                def executor = previousBuild.getExecutor()
                if (executor != null) {
                    script.echo "Aborting older build #${previousBuild.getNumber()} with description ${buildDescription}"
                    executor.interrupt(hudson.model.Result.ABORTED, new jenkins.model.CauseOfInterruption.UserInterruption(
                            "Aborted by newer build #${currentBuild.getNumber()} by description"
                    ))
                }
            }
            previousBuild = previousBuild.getPreviousBuildInProgress()
        }
    }

    def static isOlderBuildWithDescriptionRunning(Object script, String buildDescription) {
        int depth = 0
        hudson.model.Run currentBuild = script.currentBuild.rawBuild
        hudson.model.Run previousBuild = currentBuild.getPreviousBuildInProgress()

        while (previousBuild != null && depth <= MAX_DEPTH_FOR_SEARCH_SAME_BUILDS) {
            depth++
            if(previousBuild.isInProgress() && previousBuild.getDescription() == buildDescription) {
                script.echo "Build with description ${buildDescription} is running"
                return true
            }
            previousBuild = previousBuild.getPreviousBuildInProgress()
        }
        return false
    }

    def static safe(Object script, Closure body) {
        try {
            body()
        } catch (e){
            script.echo "^^^^ Ignored exception: ${e.toString()} ^^^^"
        }
    }

    def static checkPipelineParameterDefined(Object script, Object parameterValue, String parameterName){
        if(!parameterValue){
            script.error("Pipeline configuration parameter $parameterName must be set")
        }
    }

    @Deprecated
    def static applyParameterIfNotEmpty(Object script, String varName, paramValue, assignmentAction) {
        def valueNotEmpty = paramValue != null
        if (paramValue instanceof String) {
            valueNotEmpty = paramValue?.trim()
        }
        if (valueNotEmpty) {
            script.echo "{$varName} sets from parameters to {$paramValue}"
            assignmentAction(paramValue)
        }
    }

    /**
     * Firstly extract from env, if empty, extract from params
     * Value sets to env when extracted from webhook body, so it with more priority
     * @param actionWithValue {value -> }
     */
    def static extractValueFromEnvOrParamsAndRun(Object script, String key, Closure actionWithValue) {
        runWithNotEmptyValue(script, key, script.env[key], script.params[key], actionWithValue)
    }

    /**
     * @param actionWithValue {value -> }
     */
    def static extractValueFromEnvAndRun(Object script, String key, Closure actionWithValue) {
        runWithNotEmptyValue(script, key, script.env[key], null, actionWithValue)
    }

    /**
     * @param actionWithValue {value -> }
     */
    def static extractValueFromParamsAndRun(Object script, String key, Closure actionWithValue) {
        runWithNotEmptyValue(script, key, null, script.params[key], actionWithValue)
    }

    def static runWithNotEmptyValue(script, String key, envValue, paramsValue, Closure actionWithValue) {
        if (notEmpty(envValue)) {
            script.echo "Value {$envValue} extracted by {$key} from env"
            actionWithValue(envValue)
        } else if (notEmpty(paramsValue)) {
            script.echo "Value {$paramsValue} extracted by {$key} from params"
            actionWithValue(paramsValue)
        } else {
            script.echo "Value not extracted by {$key}"
        }
    }

    def static notEmpty(value) {
        if (value instanceof String) {
            return value?.trim()
        } else {
            return value != null
        }
    }

    def static printInitialVar(Object script, String varName, varValue) {
        script.echo "initial value of {$varName} is {$varValue}"
    }

    def static unsuccessReasonsToString(List<Stage> stages){
        def unsuccessReasons = ""
        StageTreeUtil.forStages(stages) { stage ->
            if(stage instanceof StageWithResult) {
                if (stage.result && stage.result != Result.SUCCESS && stage.result != Result.NOT_BUILT) {
                    if (!unsuccessReasons.isEmpty()) {
                        unsuccessReasons += ", "
                    }
                    unsuccessReasons += "${stage.name} -> ${stage.result}"
                }
            }
        }
        return unsuccessReasons
    }

    def static printInitialStageStrategies(Pipeline pipeline){
        pipeline.forStages { stage ->
            if(stage instanceof StageWithStrategy) {
                printInitialVar(pipeline.script, stage.name + ".strategy", stage.strategy)
            }
        }
    }

    /**
     * @param pipeline
     * @param strategiesFromParams - map, key - stageName, value - new strategy value
     */
    def static applyStrategiesFromParams(Pipeline pipeline, Map strategiesFromParamsMap) {
        strategiesFromParamsMap.each{ stageName, strategyValue ->
            if(strategyValue) {
                def stage = pipeline.getStage(stageName)
                if(stage == null) {
                    pipeline.script.echo "applying strategy from params skipped because stage ${stageName} missing"
                    return
                }
                if(stage instanceof StageWithStrategy) {
                    stage.strategy = strategyValue
                    pipeline.script.echo "value of ${stageName}.strategy sets from parameters to ${strategyValue}"
                } else {
                    pipeline.script.error "stage with name ${stageName} is not StageWithStrategy"
                }
            }
        }
    }

    /**
     * @param extraParams List<Object> -> List<hudson.model.ParameterValue>. Can override initial params
     * @return
     */
    def static startCurrentBuildCloneWithParams(Object script, List<Object> extraParams, boolean wait = false) {
        script.echo "start current build clone with extra params ${extraParams}"
        def Map currentBuildParams = script.params

        def allParams = []
        allParams.addAll(currentBuildParams
                .entrySet()
                .collect({script.string(name: it.key, value: "${it.value}")})
        )
        allParams.addAll(extraParams)
        script.build job: script.env.JOB_NAME, parameters: allParams, wait: wait
    }

    def static encodeUrl(string) {
        java.net.URLEncoder.encode(string, "UTF-8")
    }

    def static String removeQuotesFromTheEnds(String string) {
        return string.substring(1, string.length()-1)
    }

    def static isJobStartedByUser(Object script) {
        def result = script.currentBuild.rawBuild.getCauses()[0].toString().contains('UserIdCause')
        script.echo "isJobStartedByUser $result"
        return result
    }

    static def printStackTrace(Object script, Exception e) {
        StringWriter sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        String exceptionAsString = sw.toString()
        script.echo "Stacktrace: \n $exceptionAsString"
    }

    //TODO not work https://issues.jenkins-ci.org/browse/JENKINS-53162
    static def fixVisualizingStagesInParallelBlock(Object script) {
        if (!script.currentBuild.rawBuild.getAction(org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction))
            script.currentBuild.rawBuild.addAction(
                    new org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction(
                            new org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages(null)))
    }
}

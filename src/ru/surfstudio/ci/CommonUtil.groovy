package ru.surfstudio.ci

import ru.surfstudio.ci.pipeline.Pipeline
import ru.surfstudio.ci.stage.Stage

class CommonUtil {
    static int MAX_DEPTH_FOR_SEARCH_SAME_BUILDS = 50

    def static notifyBitbucketAboutStageStart(Object script, String stageName){
        script.bitbucketStatusNotify(
                buildState: 'INPROGRESS',
                buildKey: stageName,
                buildName: stageName
        )
    }

    def static notifyBitbucketAboutStageFinish(Object script, String stageName, String result){
        def bitbucketStatus

        switch (result){
            case Result.SUCCESS:
                bitbucketStatus = 'SUCCESSFUL'
                break
            case Result.ABORTED:
                bitbucketStatus = 'SUCCESSFUL' //todo плагин не поддерживает статус STOPPED, возможно он здесь лучше подходит
                break
            case Result.FAILURE:
            case Result.UNSTABLE:
                bitbucketStatus = 'FAILED'
                break
            default:
                script.error "Unsupported Result: ${result}"
        }
        script.bitbucketStatusNotify(
                buildState: bitbucketStatus,
                buildKey: stageName,
                buildName: stageName
        )
    }

    def static getBuildUrlHtmlLink(Object script){
        return  "<a href=\"${script.env.JENKINS_URL}blue/organizations/jenkins/${script.env.JOB_NAME}/detail/${script.env.JOB_NAME}/${script.env.BUILD_NUMBER}/pipeline\">build</a>"
    }

    def static getJiraTaskHtmlLink(String taskKey){
        return "<a href=\"${Constants.JIRA_URL}browse/${taskKey}\">${taskKey}</a>"
    }

    def static shWithRuby(Object script, String command, String version = "2.3.5") {
        script.sh "hostname; set +x; source ~/.bashrc; source ~/.rvm/scripts/rvm; rvm use $version; $command"
    }

    @Deprecated
    def static abortDuplicateBuilds(Object script, String buildIdentifier) {
        script.currentBuild.setDescription(buildIdentifier)
        tryAbortOlderBuildsWithDescription(script, buildIdentifier)
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
                script.echo "build with description ${buildDescription} is running"
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

    def static printInitialVar(Object script, String varName, varValue) {
        script.echo "initial value of {$varName} is {$varValue}"
    }

    def static unsuccessReasonsToString(List<Stage> stages){
        def unsuccessReasons = ""
        for (stage in stages) {
            if (stage.result && stage.result != Result.SUCCESS) {
                if (!unsuccessReasons.isEmpty()) {
                    unsuccessReasons += ", "
                }
                unsuccessReasons += "${stage.name} -> ${stage.result}"
            }
        }
        return unsuccessReasons
    }

    def static printInitialStageStrategies(Pipeline pipeline){
        for (stage in pipeline.stages) {
            printInitialVar(pipeline.script, stage.name + ".strategy", stage.strategy)
        }
    }

    /**
     * @param pipeline
     * @param strategiesFromParams - map, key - stageName, value - new strategy value
     */
    def static applyStrategiesFromParams(Pipeline pipeline, Map strategiesFromParamsMap) {
        strategiesFromParamsMap.each{ stageName, strategyValue ->
            if(strategyValue) {
                pipeline.getStage(stageName).strategy = strategyValue
                pipeline.script.echo "value of ${stageName}.strategy sets from parameters to ${strategyValue}"
            }
        }
    }

    def static startCurrentBuildCloneWithParams(Object script, ArrayList<Object> extraParams) {
        script.echo "start current build clone with extra params ${extraParams}"
        def Map currentBuildParams = script.params

        def allParams = []
        allParams.addAll(extraParams)
        allParams.addAll(currentBuildParams
                .entrySet()
                .collect({script.string(name: it.key, value: it.value)})
        )
        script.build job: script.env.JOB_NAME, parameters: allParams, wait: false
    }
}

package ru.surfstudio.ci

import ru.surfstudio.ci.pipeline.Pipeline

class CommonUtil {

    def static notifyBitbucketAboutStageStart(Object script, String stageName){
        script.bitbucketStatusNotify(
                buildState: 'INPROGRESS',
                buildKey: stageName,
                buildName: stageName
        )
    }

    def static notifyBitbucketAboutStageFinish(Object script, String stageName, boolean success){
        def bitbucketStatus = success ?
                'SUCCESSFUL' :
                'FAILED'
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
        script.sh "set +x; source /home/jenkins/.bashrc; source /usr/share/rvm/scripts/rvm; rvm use $version; $command"
    }

    def static abortDuplicateBuilds(Object script, String buildIdentifier) {
        hudson.model.Run currentBuild = script.currentBuild.rawBuild
        currentBuild.setDescription(buildIdentifier)
        hudson.model.Run previousBuild = currentBuild.getPreviousBuildInProgress()

        while (previousBuild != null) {
            if (previousBuild.isInProgress() && previousBuild.getDescription() == currentBuild.getDescription()) {
                def executor = previousBuild.getExecutor()
                if (executor != null) {
                    script.echo ">> Aborting older build #${previousBuild.getNumber()}"
                    executor.interrupt(hudson.model.Result.ABORTED, new jenkins.model.CauseOfInterruption.UserInterruption(
                            "Aborted by newer build #${currentBuild.getNumber()}"
                    ))
                }
            }

            previousBuild = previousBuild.getPreviousBuildInProgress()
        }
    }

    def static safe(Object script, Closure body) {
        try {
            body()
        } catch (e){
            script.sh "Ignored exception: ${e.message}"
        }
    }

    def static applyParameterIfNotEmpty(Object script, String varName, paramValue, assignmentAction) {
        if (paramValue?.trim()) {
            script.echo "value of {$varName} sets from parameters to {$paramValue}"
            assignmentAction(paramValue)
        }
    }

    def static printInitialVar(Object script, String varName, varValue) {
        script.echo "initial value of {$varName} is {$varValue}"
    }

    def static unsuccessReasonsToString(Pipeline pipeline){
        def unsuccessReasons = ""
        for (stage in pipeline.stages) {
            if (stage.result && stage.result != Result.SUCCESS) {
                if (!unsuccessReasons.isEmpty()) {
                    unsuccessReasons += ", "
                }
                unsuccessReasons += "${stage.name} -> ${stage.result}"
            }
        }
        return unsuccessReasons
    }

    def static printDefaultStageStrategies(Pipeline pipeline){
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
}

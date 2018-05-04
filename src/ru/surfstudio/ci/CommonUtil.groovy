package ru.surfstudio.ci

import ru.surfstudio.ci.pipeline.Pipeline
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageStrategy

class CommonUtil {

     def static stageWithStrategy(Pipeline ctx, Stage stage) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
         def script = ctx.script
         script.stage(stage.name) {
            if (stage.strategy == StageStrategy.SKIP_STAGE) {
                return
            } else {
                try {
                    notifyBitbucketAboutStageStart(script, stage.name)
                    stage.body()
                    stage.result = Result.SUCCESS
                } catch (e) {
                    if (stage.strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                        stage.result = Result.FAILURE
                        ctx.jobResult = Result.FAILURE
                        throw e
                    } else if (stage.strategy == StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                        stage.result = Result.UNSTABLE
                        if (ctx.jobResult != Result.FAILURE) {
                            ctx.jobResult = Result.UNSTABLE
                        }
                    } else if (stage.strategy == StageStrategy.SUCCESS_WHEN_STAGE_ERROR) {
                        stage.result = Result.SUCCESS
                    }  else {
                        script.error("Unsupported strategy " + stage.strategy)
                    }
                } finally {
                    notifyBitbucketAboutStageFinish(script, stage.name, stage.result == Result.SUCCESS)
                }
            }
        }
    }

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
        return  "<a href=\"${script.JENKINS_URL}blue/organizations/jenkins/${script.JOB_NAME}/detail/${script.JOB_NAME}/${script.BUILD_NUMBER}/pipeline\">build</a>"
    }

    def static getJiraTaskHtmlLink(String taskKey){
        return "<a href=\"${Constants.JIRA_URL}browse/${taskKey}\">${taskKey}</a>"
    }

    void shWithRuby(Object script, String command, String version = "2.3.5") {
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

    def static printDefaultVar(Object script, String varName, varValue) {
        script.echo "default value of {$varName} is {$varValue}"
    }

    def static unsuccessReasonsToString(Pipeline pipeline){
        def unsuccessReasons = ""
        for (stage in pipeline.stages) {
            if (stage.result != Result.SUCCESS) {
                if (!unsuccessReasons.isEmpty()) {
                    unsuccessReasons += ", "
                }
                unsuccessReasons += "${stage.name} -> ${stage.result}"
            }
        }
        return unsuccessReasons
    }
}

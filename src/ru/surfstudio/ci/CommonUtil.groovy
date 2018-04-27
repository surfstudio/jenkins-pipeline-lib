package ru.surfstudio.ci


class CommonUtil {

    public static void stageWithStrategy(BaseContext ctx, String stageName, String strategy, stageBody) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
        ctx.origin.stage(stageName) {
            if (strategy == StageStrategy.SKIP_STAGE) {
                return
            } else {
                try {
                    ctx.origin.bitbucketStatusNotify(
                            buildState: 'INPROGRESS',
                            buildKey: stageName,
                            buildName: stageName
                    )
                    stageBody()
                    ctx.stageResults.put(stageName, Result.SUCCESS)
                } catch (e) {

                    if (strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                        ctx.stageResults.put(stageName, Result.FAILURE)
                        ctx.jobResult = Result.FAILURE
                        throw e
                    } else if (strategy == StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                        ctx.stageResults.put(stageName, Result.UNSTABLE)
                        if (ctx.jobResult != Result.FAILURE) {
                            ctx.jobResult = Result.UNSTABLE
                        }
                    } else if (strategy == StageStrategy.SUCCESS_WHEN_STAGE_ERROR) {
                        ctx.stageResults.put(stageName, Result.SUCCESS)
                    } else {
                        ctx.origin.error("Unsupported strategy " + strategy)
                    }
                } finally {
                    String bitbucketStatus = ctx.stageResults.get(stageName) == Result.SUCCESS ?
                            'SUCCESSFUL' :
                            'FAILED'
                    ctx.origin.bitbucketStatusNotify(
                            buildState: bitbucketStatus,
                            buildKey: stageName,
                            buildName: stageName
                    )
                }
            }
        }
    }

    public static void abortPreviousBuilds(BaseContext ctx, String jobIdentifier) {
        ctx.origin.currentBuild.description = jobIdentifier
        Run previousBuild = ctx.origin.currentBuild.rawBuild.getPreviousBuildInProgress()

        while (previousBuild != null) {
            if (previousBuild.isInProgress() && previousBuild.getDescription() == ctx.origin.currentBuild.description) {
                def executor = previousBuild.getExecutor()
                if (executor != null) {
                    ctx.origin.echo ">> Aborting older build #${previousBuild.number}"
                    executor.interrupt(Result.ABORTED, new UserInterruption(
                            "Aborted by newer build #${ctx.origin.currentBuild.number}"
                    ))
                }
            }

            previousBuild = previousBuild.getPreviousBuildInProgress()
        }
    }

    public static void applyParameterIfNotEmpty(BaseContext ctx, String varName, paramValue, assignmentAction) {
        if (paramValue?.trim()) {
            ctx.origin.echo "value of {$varName} sets from parameters to {$paramValue}"
            assignmentAction(paramValue)
        }
    }

    public static void printDefaultVar(BaseContext ctx, String varName, varValue) {
        ctx.origin.echo "default value of {$varName} is {$varValue}"
    }
}

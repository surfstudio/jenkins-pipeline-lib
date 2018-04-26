package ru.surfstudio.ci


class CommonUtil {

    public static void abortPreviousBuilds(BaseContext ctx, String jobIdentifier) { //todo
        /*ctx.origin.currentBuild.description = jobIdentifier
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
        }*/
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

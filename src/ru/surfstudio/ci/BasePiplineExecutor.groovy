package ru.surfstudio.ci

abstract class BasePiplineExecutor<C extends BaseContext> {
    protected C ctx

    BasePiplineExecutor(C ctx) {
        this.ctx = ctx
    }

    abstract void run()


    protected void stageWithStrategy(String stageName, String strategy, stageBody) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
        ctx.script.stage(stageName) {
            if (strategy == StageStartegy.SKIP_STAGE) {
                return
            } else {
                try {
                    ctx.script.bitbucketStatusNotify(
                            buildState: 'INPROGRESS',
                            buildKey: stageName,
                            buildName: stageName
                    )
                    stageBody()
                    ctx.stageResults.put(stageName, Result.SUCCESS)
                } catch (e) {

                    if (strategy == StageStartegy.FAIL_WHEN_STAGE_ERROR) {
                        ctx.stageResults.put(stageName, Result.FAILURE)
                        ctx.jobResult = Result.FAILURE
                        throw e
                    } else if (strategy == StageStartegy.UNSTABLE_WHEN_STAGE_ERROR) {
                        ctx.stageResults.put(stageName, Result.UNSTABLE)
                        if (ctx.jobResult != Result.FAILURE) {
                            ctx.jobResult = Result.UNSTABLE
                        }
                    } else if (strategy == StageStartegy.SUCCESS_WHEN_STAGE_ERROR) {
                        ctx.stageResults.put(stageName, Result.SUCCESS)
                    } else {
                        ctx.script.error("Unsupported strategy " + strategy)
                    }
                } finally {
                    String bitbucketStatus = ctx.stageResults.get(stageName) == Result.SUCCESS ?
                            'SUCCESSFUL' :
                            'FAILED'
                    ctx.script.bitbucketStatusNotify(
                            buildState: bitbucketStatus,
                            buildKey: stageName,
                            buildName: stageName
                    )
                }
            }
        }
    }
}

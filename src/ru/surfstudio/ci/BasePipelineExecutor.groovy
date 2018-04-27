package ru.surfstudio.ci

abstract class BasePipelineExecutor<C extends BaseContext> implements Serializable {
    protected C ctx

    BasePipelineExecutor(C ctx) {
        this.ctx = ctx
    }

    abstract void run()


    protected void stageWithStrategy(String stageName, String strategy, stageBody) {
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
}

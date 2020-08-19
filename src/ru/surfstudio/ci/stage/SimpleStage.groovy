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
package ru.surfstudio.ci.stage

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Standard Stage
 */
class SimpleStage implements Stage, StageWithStrategy, StageWithResult {
    String name
    Closure body
    String strategy //see class StageStrategy
    String result  //see class Result, possible values: NOT_BUILT, SUCCESS, ABORTED, FAILURE
    boolean runPreAndPostExecuteStageBodies = true

    SimpleStage(String name, String strategy, Closure body) {
        this(name, strategy, true, body);
    }

    SimpleStage(String name, String strategy, boolean runPreAndPostExecuteStageBodies, Closure body) {
        this.name = name
        this.body = body
        this.runPreAndPostExecuteStageBodies = runPreAndPostExecuteStageBodies
        this.strategy = strategy
    }

    @Override
    void execute(Object script, Pipeline context) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
        def stage = this
        script.stage(stage.name) {
            if (!stage.strategy || stage.strategy == StageStrategy.UNDEFINED) {
                script.error "Stage strategy is undefined, you must specify it"
            }
            if (stage.strategy == StageStrategy.SKIP_STAGE) {
                stage.result = Result.NOT_BUILT
                return
            } else {
                try {
                    script.echo("Stage \"${stage.name}\" STARTED")
                    if (context.preExecuteStageBody && runPreAndPostExecuteStageBodies) {
                        context.preExecuteStageBody(stage)
                    }
                    stage.body()
                    stage.result = Result.SUCCESS
                    script.echo("Stage \"${stage.name}\" SUCCESS")
                } catch (e) {
                    script.echo "Error: ${e.toString()}"
                    CommonUtil.printStackTrace(script, e)

                    if (e instanceof InterruptedException || //отменено из другого процесса
                            e instanceof hudson.AbortException && e.getMessage() == "script returned exit code 143") {
                        //отменено пользователем
                        script.echo("Stage \"${stage.name}\" ABORTED")
                        stage.result = Result.ABORTED
                        throw e
                    } else {
                        script.echo("Stage \"${stage.name}\" FAIL")
                        script.echo("Stage strategy: ${stage.strategy}")
                        stage.result = Result.FAILURE
                        if (stage.strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                            throw e
                        } else if (stage.strategy == StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                            script.unstable("${e.toString()}")
                        }
                    }
                } finally {
                    if (context.postExecuteStageBody && runPreAndPostExecuteStageBodies) {
                        context.postExecuteStageBody(stage)
                    }
                }
            }
        }
    }

    /**
     * call action after current body
     * @param action: { SimpleStage => ... }
     */
    void afterBody(Closure action) {
        def prevBody = this.body
        this.body = {
            this.body()
            action(this)
        }
    }

    /**
     * call action before current body
     * @param action: { SimpleStage => ... }
     */
    void beforeBody(Closure action) {
        def prevBody = this.body
        this.body = {
            action(this)
            this.body()
        }
    }
}

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

/**
 * Stage для {@link ru.surfstudio.ci.pipeline.Pipeline}
 */
class Stage implements StageInterface {
    String name
    String node = null
    Closure body
    String strategy //see class StageStrategy
    String result  //see class StageResult

    Stage(String name, String strategy, Closure body) {
        this(name, strategy, null, body)
    }

    Stage(String name, String strategy, String node, Closure body) {
        this.name = name
        this.node = node
        this.body = body
        this.strategy = strategy
    }

    @Override
    def execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
        def stage = this
        script.node(stage.node) {
            script.stage(stage.name) {
                if (!stage.strategy || stage.strategy == StageStrategy.UNDEFINED) {
                    script.error "Stage strategy is undefined, you must specify it"
                }
                if (stage.strategy == StageStrategy.SKIP_STAGE) {
                    stage.result = Result.NOT_BUILT
                    org.jenkinsci.plugins.pipeline.modeldefinition.Utils.markStageSkippedForConditional(stage.name)
                    return
                } else {
                    try {
                        script.echo("Stage ${stage.name} STARTED")
                        if (preExecuteStageBody) {
                            preExecuteStageBody(stage)
                        }
                        stage.body()
                        stage.result = Result.SUCCESS
                        script.echo("Stage ${stage.name} SUCCESS")
                    } catch (e) {
                        script.echo "Error: ${e.toString()}"
                        CommonUtil.printStackTrace(script, e)

                        if (e instanceof InterruptedException || //отменено из другого процесса
                                e instanceof hudson.AbortException && e.getMessage() == "script returned exit code 143") {
                            //отменено пользователем
                            script.echo("Stage ${stage.name} ABORTED")
                            stage.result = Result.ABORTED
                            throw e
                        } else {
                            script.echo("Stage ${stage.name} FAIL")
                            script.echo("Stage strategy: ${stage.strategy}")
                            stage.result = Result.FAILURE
                            if (stage.strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                                throw e
                            }
                        }
                    } finally {
                        if (postExecuteStageBody) {
                            postExecuteStageBody(stage)
                        }
                    }
                }
            }
        }
    }
}

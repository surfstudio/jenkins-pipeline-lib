#!/usr/bin/groovy
package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageStrategy

abstract class Pipeline implements Serializable {

    public script //Jenkins Pipeline Script
    public jobResult = Result.SUCCESS
    public List<Stage> stages
    public Closure finalizeBody
    public node

    Pipeline(script) {
        this.script = script
    }

    /**
     * Инициализацию пайплайна нужно проводить здесь вместо конструктора из-за особенностей рантайм выполенния
     * https://issues.jenkins-ci.org/browse/JENKINS-26313
     */
    def init() {

    }

    def run() {
        script.node(node) {
            try {
                for (Stage stage : stages) {
                    stageWithStrategy(this, stage)
                }
            }  finally {
                script.echo "Finalize build:"
                script.currentBuild.result = jobResult
                finalizeBody()
            }
        }
    }

    def stageWithStrategy(Stage stage) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
        script.stage(stage.name) {
            if (stage.strategy == StageStrategy.SKIP_STAGE) {
                return
            } else {
                try {
                    script.echo("stage ${stage.name} started")
                    CommonUtil.notifyBitbucketAboutStageStart(script, stage.name)
                    stage.body()
                    stage.result = Result.SUCCESS
                    script.echo("stage ${stage.name} success")
                } catch (e) {
                    script.echo("stage ${stage.name} fail")
                    script.echo("apply stage strategy: ${stage.strategy}")
                    if (stage.strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                        stage.result = Result.FAILURE
                        jobResult = Result.FAILURE
                        throw e
                    } else if (stage.strategy == StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                        stage.result = Result.UNSTABLE
                        if (jobResult != Result.FAILURE) {
                            jobResult = Result.UNSTABLE
                        }
                    } else if (stage.strategy == StageStrategy.SUCCESS_WHEN_STAGE_ERROR) {
                        stage.result = Result.SUCCESS
                    }  else {
                        script.error("Unsupported strategy " + stage.strategy)
                    }
                } finally {
                    CommonUtil.notifyBitbucketAboutStageFinish(script, stage.name, stage.result == Result.SUCCESS)
                }
            }
        }
    }

    def replaceStage(Stage newStage) {
        for(int i = 0; i < stages.size(); i++){
            if(stages.get(i).name == newStage.name) {
                stages.remove(i)
                stages.add(i, newStage)
                return
            }
        }
    }

    def getStage(String stageName) {
        for(Stage stage in  stages){
            if(stage.name == stageName) {
                return stage
            }
        }
        script.error("stage with name ${stageName} doesn't exist in pipeline")
    }

    def static createStage(String name, String strategy, Closure body){
        return new Stage(name, strategy, body)
    }
}

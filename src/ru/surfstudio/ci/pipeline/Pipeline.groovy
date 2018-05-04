#!/usr/bin/groovy
package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.Result
import ru.surfstudio.ci.stage.Stage

import static ru.surfstudio.ci.CommonUtil.stageWithStrategy


abstract class Pipeline implements Serializable {

    public script //Jenkins Pipeline Script
    public jobResult = Result.SUCCESS
    public List<Stage> stages
    public Closure finalizeBody
    public node

    Pipeline(script) {
        script.echo "DEL ppl construct"
        this.script = script
    }

    def init() {

    }

    def run() {
        script.node(node) {
            try {
                for(Stage stage : stages) {
                    stageWithStrategy(this, stage)
                }
            } finally {
                script.currentBuild.result = jobResult
                finalizeBody()
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

    def static createStage(String name, String strategy, Closure body){
        return new Stage(name, strategy, body)
    }
}

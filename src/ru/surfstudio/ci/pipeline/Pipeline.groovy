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

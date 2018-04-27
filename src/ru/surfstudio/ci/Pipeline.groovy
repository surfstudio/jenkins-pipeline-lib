#!/usr/bin/groovy
package ru.surfstudio.ci

import ru.surfstudio.ci.Stage

import static ru.surfstudio.ci.CommonUtil.stageWithStrategy


abstract class Pipeline implements Serializable {

    public origin //Jenkins Pipeline Script context
    public jobResult = Result.SUCCESS
    public stages
    public Closure finalize
    public node

    Pipeline(origin) {
        this.origin = origin
    }

    public void run() {
        origin.node(node) {
            try {
                for(Stage stage : stages) {
                    if(stage.strategy){
                        stageWithStrategy(this, stage.name, stage.strategy, stage.body)
                    } else {
                        origin.stage(stage.name, stage.body)
                    }
                }
            } finally {
                finalize()
            }
        }
    }
}

package ru.surfstudio.ci.stage

/**
 * Stage для {@link ru.surfstudio.ci.pipeline.base.Pipeline}
 */
class Stage {
    String name
    Closure body
    String strategy //see class StageStrategy
    String result  //see class StageResult

    Stage(String name, String strategy, Closure body) {
        this.name = name
        this.body = body
        this.strategy = strategy
    }
}

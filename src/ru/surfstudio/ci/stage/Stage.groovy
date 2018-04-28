package ru.surfstudio.ci.stage


class Stage {
    String name
    Closure body
    String strategy //StageStrategy
    String result  //StageResult

    Stage(String name, String strategy, Closure body) {
        this.name = name
        this.body = body
        this.strategy = strategy
    }
}

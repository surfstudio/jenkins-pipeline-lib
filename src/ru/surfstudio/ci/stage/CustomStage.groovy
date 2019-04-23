package ru.surfstudio.ci.stage

/**
 * Позволяет создавтаь полностью кастомные элементы пайплайна
 */
public class CustomStage implements Stage {
    String name
    Closure body

    public CustomStage(String name, Closure body) {
        this.name = name
        this.body = body
    }

    @Override
    void execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        script.echo("Custom stage \"${name}\" STARTED")
        try {
            body()
        } finally {
            script.echo("Custom stage \"${name}\" FINISHED")
        }
    }
}

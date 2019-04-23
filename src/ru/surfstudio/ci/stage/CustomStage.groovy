package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

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
    void execute(Object script, Pipeline context) {
        script.echo("Custom stage \"${name}\" STARTED")
        try {
            body()
        } finally {
            script.echo("Custom stage \"${name}\" FINISHED")
        }
    }
}

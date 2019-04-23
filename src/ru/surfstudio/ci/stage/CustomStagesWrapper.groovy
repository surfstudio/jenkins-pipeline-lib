package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Позволяет создавтаь полностью кастомные элементы пайплайна
 */
public class CustomStagesWrapper extends AbstractStagesWrapper {

    public CustomStagesWrapper(String name, Closure stagesWrapperFunction, List<Stage> stages) {
        super(name, stagesWrapperFunction, stages)
    }

    @Override
    void execute(Object script, Pipeline context) {
        script.echo("Custom stages wrapper \"${name}\" STARTED")
        try {
            super.execute(script, context)
        } finally {
            script.echo("Custom stages wrapper \"${name}\" FINISHED")
        }
    }
}

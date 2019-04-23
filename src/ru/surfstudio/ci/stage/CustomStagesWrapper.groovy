package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Wrap execution of stages on user code
 * stagesWrapperFunction gets parameters (script, Closure executeStagesBody),
 * where executeStagesBody - function, which execute stages
 */
public class CustomStagesWrapper extends AbstractStagesWrapper {
    Closure stagesWrapperFunction

    public CustomStagesWrapper(String name, Closure stagesWrapperFunction, List<Stage> stages) {
        super(name, stages)
        this.stagesWrapperFunction = stagesWrapperFunction
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

    @Override
    def wrapStages(Object script, Pipeline context, Closure executeStagesBody) {
      stagesWrapperFunction(script, executeStagesBody)
    }
}

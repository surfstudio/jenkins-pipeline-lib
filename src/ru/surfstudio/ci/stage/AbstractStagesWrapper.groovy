package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Base wrapper over stages execution
 * It wrap stages execution in function stagesWrapperFunction
 * stagesWrapperFunction gets arguments (script, Pipeline context, Closure executeStagesBody),
 * where executeStagesBody - function, which execute stages
 */
public abstract class AbstractStagesWrapper implements StageGroup {
    String name
    Closure stagesWrapperFunction
    List<Stage> stages

    public AbstractStagesWrapper(String name, Closure stagesWrapperFunction, List<Stage> stages) {
        this.name = name
        this.stagesWrapperFunction = stagesWrapperFunction
        this.stages = stages
    }

    @Override
    void execute(Object script, Pipeline context) {
        def executeStagesBody = {
            for (Stage stage in stages) {
                stage.execute(script, context)
            }
        }
        stagesWrapperFunction(script, context, executeStagesBody)
    }
}

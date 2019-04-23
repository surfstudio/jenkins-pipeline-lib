package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Базовый класс для оберток над стейджами
 * Позволяет обернуть выполнение стейджей в некоторый код
 * stagesWrapperFunction - функция для обертки, принимает парамтры (script, executeStagesBody), где executeStagesBody
 * - функция выполняющая стейджи
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
        stagesWrapperFunction(script, executeStagesBody)
    }
}

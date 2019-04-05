package ru.surfstudio.ci.stage;

public interface StageInterface {
    String getName()
    execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody)

}


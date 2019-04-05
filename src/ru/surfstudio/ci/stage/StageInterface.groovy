package ru.surfstudio.ci.stage;

public interface StageInterface {
    String getName()
    void execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody)

}


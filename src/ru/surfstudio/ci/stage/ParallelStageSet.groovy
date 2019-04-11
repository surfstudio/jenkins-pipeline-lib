package ru.surfstudio.ci.stage

public class ParallelStageSet implements StageGroup {
    String name
    List<StageInterface> stages = Collections.emptyList()


    public ParallelStageSet(String name, List<StageInterface> stages) {
        this.name = name
        this.stages = stages
    }

    @Override
    void execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        def lines = [:]
        for (StageInterface stage in stages) {
            def tmpStage = stage //fix override stage parameter in closure
            lines[stage.name] = { tmpStage.execute(script, preExecuteStageBody, postExecuteStageBody) }
        }
        script.stage(name) {
            script.parallel lines
        }
    }
}

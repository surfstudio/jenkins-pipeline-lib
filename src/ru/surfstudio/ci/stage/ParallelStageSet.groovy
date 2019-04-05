package ru.surfstudio.ci.stage

public class ParallelStageSet implements StageGroup {
    String name
    List<StageInterface> stages = Collections.emptyList()


    public ParallelStageSet(String name, List<StageInterface> stages) {
        this.name = name
        this.stages = stages
    }

    @Override
    def execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        def lines = [:]
        for (StageInterface stage in stages) {
            lines[stage.name] = {stage.execute(script, preExecuteStageBody, postExecuteStageBody)}
        }
        script.parallel lines
    }
}

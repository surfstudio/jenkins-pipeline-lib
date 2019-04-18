package ru.surfstudio.ci.stage

//TODO не поддерживается набора стейджей для каждой ветки https://issues.jenkins-ci.org/browse/JENKINS-53162
public class ParallelStageSet implements StageGroup {
    String name
    List<Stage> stages = Collections.emptyList()
    boolean copyWorkspace = true


    public ParallelStageSet(String name, boolean copyWorkspace, List<Stage> stages) {
        this.name = name
        this.stages = stages
        this.copyWorkspace = copyWorkspace
    }

    @Override
    void execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        def stashName = 'workspace'
        if(copyWorkspace){
            script.stash includes: '**', name: stashName
        }
        def lines = [:]
        for (Stage stage in stages) {
            def tmpStage = stage //fix override stage parameter in closure
            lines[stage.name] = {
                if(copyWorkspace) {
                    script.unstash stashName
                }
                tmpStage.execute(script, preExecuteStageBody, postExecuteStageBody) }
        }
        script.stage(name) {
            script.parallel lines
        }
    }
}

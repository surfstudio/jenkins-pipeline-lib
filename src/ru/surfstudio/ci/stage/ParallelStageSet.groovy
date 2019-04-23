package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Run stages parallel
 */
//TODO не поддерживается набор стейджей для каждой ветки https://issues.jenkins-ci.org/browse/JENKINS-53162
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
    void execute(Object script, Pipeline context) {
        def stashName = "${script.env.JOB_NAME}_${script.env.BUILD_NUMBER}_workspace"
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
                tmpStage.execute(script, context) }
        }
        script.stage(name) {
            script.parallel lines
        }
    }
}

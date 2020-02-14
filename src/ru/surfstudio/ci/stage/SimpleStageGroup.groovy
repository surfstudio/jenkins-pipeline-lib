package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageGroup

/**
 * Simple implementation of StageGroup
 *
 * Give possibility to create stage consist of list of stages
 */
class SimpleStageGroup implements StageGroup {

    String name
    List<Stage> stages = Collections.emptyList()
    boolean copyWorkspace = true

    SimpleStageGroup(String name, boolean copyWorkspace, List<Stage> stages) {
        this.name = name
        this.stages = stages
        this.copyWorkspace = copyWorkspace
    }

    @Override
    void execute(Object script, Pipeline context) {
        def stashName = "${script.env.JOB_NAME}_${script.env.BUILD_NUMBER}_workspace"
        if (copyWorkspace) {
            script.stash includes: '**', name: stashName
        }
        for (Stage stage in stages) {
            if (copyWorkspace) {
                script.unstash stashName
            }

            stage.execute(script, context)
        }
    }
}
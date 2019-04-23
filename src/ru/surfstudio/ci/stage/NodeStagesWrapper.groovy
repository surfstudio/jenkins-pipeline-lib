package ru.surfstudio.ci.stage

/**
 * обертка для запуска стейджей на ноде, отличной от ноды на которой выполняется пайплайн
 */
public class NodeStagesWrapper implements StageGroup {
    String name
    String node
    List<Stage> stages = Collections.emptyList()
    boolean copyWorkspace = true


    public NodeStagesWrapper(String name, String node, boolean copyWorkspace, List<Stage> stages) {
        this.name = name
        this.node = node
        this.stages = stages
        this.copyWorkspace = copyWorkspace
    }

    @Override
    void execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        def stashName = "${script.env.JOB_NAME}_${script.env.BUILD_NUMBER}_workspace"
        if(copyWorkspace){
            script.stash includes: '**', name: stashName
        }
        script.node(node) {
            script.echo "Switch to node ${node}: ${script.env.NODE_NAME}"
            if(copyWorkspace) {
                script.unstash stashName
            }
            for (Stage stage in stages) {
                stage.execute(script, preExecuteStageBody, postExecuteStageBody)
            }
        }
        script.echo "Switch back to previous node: ${script.env.NODE_NAME}"


    }
}

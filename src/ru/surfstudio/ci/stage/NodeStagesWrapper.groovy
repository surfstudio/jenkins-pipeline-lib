package ru.surfstudio.ci.stage

/**
 * обертка для запуска стейджей на ноде, отличной от ноды на которой выполняется пайплайн
 */
public class NodeStagesWrapper extends AbstractStagesWrapper {
    String node
    boolean copyWorkspace = true
    final def nodeWrapperFunction = {
        script, executeStagesBody ->
            def stashName = "${script.env.JOB_NAME}_${script.env.BUILD_NUMBER}_workspace"
            if(copyWorkspace){
                script.stash includes: '**', name: stashName
            }
            script.node(node) {
                script.echo "Switch to node ${node}: ${script.env.NODE_NAME}"
                if(copyWorkspace) {
                    script.unstash stashName
                }
                executeStagesBody()
            }
            script.echo "Switch back to previous node: ${script.env.NODE_NAME}"

    }



    public NodeStagesWrapper(String name, String node, boolean copyWorkspace, List<Stage> stages) {
        super(name, nodeWrapperFunction, stages)
        this.node = node
        this.copyWorkspace = copyWorkspace
    }
}

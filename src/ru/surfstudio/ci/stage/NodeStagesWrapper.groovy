package ru.surfstudio.ci.stage

/**
 * Wrapper for run stages on specific node
 */
public class NodeStagesWrapper extends AbstractStagesWrapper {
    String node
    boolean copyWorkspace = true

    public NodeStagesWrapper(String name, String node, boolean copyWorkspace, List<Stage> stages) {
        super(name, {}, stages)
        this.node = node
        this.copyWorkspace = copyWorkspace
        this.stagesWrapperFunction = {
            script, context, executeStagesBody ->
                def stashName = "${script.env.JOB_NAME}_${script.env.BUILD_NUMBER}_workspace"
                if(copyWorkspace){
                    script.stash includes: '**', name: stashName
                }
                try {
                    script.node(node) {
                        script.echo "Switch to node ${node}: ${script.env.NODE_NAME}"
                        if (copyWorkspace) {
                            script.unstash stashName
                        }
                        executeStagesBody()
                    }
                } finally {
                    script.echo "Switch back to previous node: ${script.env.NODE_NAME}"
                }

        }

    }
}

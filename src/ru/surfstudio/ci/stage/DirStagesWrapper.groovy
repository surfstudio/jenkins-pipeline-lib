package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Wrapper for run stages on specific node
 */
public class DirStagesWrapper extends AbstractStagesWrapper {
    String dir

    public DirStagesWrapper(String name, String dir,List<Stage> stages) {
        super(name, stages)
        this.dir = dir
    }

    @Override
    def wrapStages(Object script, Pipeline context, Closure executeStagesBody) {
        try {
            script.dir(dir) {
                script.echo "Switch to dir: ${dir}"
                executeStagesBody()
            }
        } finally {
            script.echo "Switch back from dir: ${dir}"
        }
    }
}

package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Wrapper for stages which work inside docker container
 */
class DockerStagesWrapper extends AbstractStagesWrapper {

    DockerStagesWrapper(String name, List<Stage> stages) {
        super(name, stages)
    }

    @Override
    def wrapStages(Object script, Pipeline context, Closure executeStagesBody) {
        script.echo "Wrap into docker container"
        script.docker.image("cirrusci/flutter:latest").inside {
            script.echo "Inside docker"
            executeStagesBody()
        }
        return null
    }
}

package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Wrapper for stages which work inside docker container
 */
class DockerStagesWrapper extends AbstractStagesWrapper {
    String imageName

    DockerStagesWrapper(String name, String imageName, List<Stage> stages) {
        super(name, stages)

        this.imageName = imageName
    }

    @Override
    def wrapStages(Object script, Pipeline context, Closure executeStagesBody) {
        script.echo "Wrap into docker container"
        //todo add possibility to change image
        script.docker.image(imageName).inside {
            script.echo "Inside docker"
            executeStagesBody()
        }
        return null
    }
}

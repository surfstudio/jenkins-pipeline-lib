package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Wrapper for stages which work inside docker container
 */
class DockerStagesWrapper extends AbstractStagesWrapper {
    String imageName
    String arguments

    DockerStagesWrapper(String name = "Docker", String imageName, String arguments = "",  List<Stage> stages) {
        super(name, stages)

        this.imageName = imageName
        this.arguments = arguments
    }

    @Override
    def wrapStages(Object script, Pipeline context, Closure executeStagesBody) {
        try {
            script.echo "Dive into docker container: $imageName"
            script.docker.image(imageName).inside(arguments) {
                script.echo "Inside docker..."
                executeStagesBody()
            }
        } finally {
            script.echo "Return from docker"
        }

    }
}

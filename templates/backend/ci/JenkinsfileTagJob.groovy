@Library('surf-lib@backend') //todo изменить версию на основную
import ru.surfstudio.ci.pipeline.tag.TagPipelineBackend

def pipeline = new TagPipelineBackend(this)
pipeline.init()
//TODO pipeline.registryPathAndProjectId = "surf-infrastructure/some/registry/path" you have to specify the path in registry for result images
//pipeline.dockerImageForBuild = "gradle:6.0.1-jdk11" you can setup docker image for container for building your application
//pipeline.registryUrl = "eu.gcr.io" you can setup another google cloud registry
pipeline.run()
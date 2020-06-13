@Library('surf-lib@backend')
import ru.surfstudio.ci.pipeline.tag.TagPipelineBackend

def pipeline = new TagPipelineBackend(this)
pipeline.init()
pipeline.registryPathAndProjectId = "surf-infrastructure/template/template"
pipeline.run()
@Library('surf-lib@version-4.0.0-SNAPSHOT')
import ru.surfstudio.ci.pipeline.pr.PrPipelineBackend

def pipeline = new PrPipelineBackend(this)
pipeline.init()

pipeline.run()
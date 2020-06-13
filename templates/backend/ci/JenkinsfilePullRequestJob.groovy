@Library('surf-lib@backend')
import ru.surfstudio.ci.pipeline.pr.PrPipelineBackend

def pipeline = new PrPipelineBackend(this)
pipeline.init()
pipeline.run()
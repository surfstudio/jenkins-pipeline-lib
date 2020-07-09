@Library('surf-lib@backend') //todo изменить версию на основную
import ru.surfstudio.ci.pipeline.pr.PrPipelineBackend

def pipeline = new PrPipelineBackend(this)
pipeline.init()
pipeline.run()
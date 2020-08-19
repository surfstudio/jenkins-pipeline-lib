@Library('surf-lib@version-4.0.0-SNAPSHOT')
import ru.surfstudio.ci.pipeline.pr.PrPipelineBackend
import ru.surfstudio.ci.stage.StageStrategy

def pipeline = new PrPipelineBackend(this)
pipeline.init()

pipeline.getStage(PrPipelineBackend.CODE_STYLE_FORMATTING).strategy = StageStrategy.SKIP_STAGE
pipeline.getStage(PrPipelineBackend.UPDATE_CURRENT_COMMIT_HASH_AFTER_FORMAT).strategy = StageStrategy.SKIP_STAGE


pipeline.run()
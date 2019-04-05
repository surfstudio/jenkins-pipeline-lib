@Library('surf-lib@version-1.0.0-SNAPSHOT') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/
import ru.surfstudio.ci.pipeline.pr.PrPipelineFlutter
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new PrPipelineFlutter(this)
pipeline.init()

//customization
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE

//run
pipeline.run()

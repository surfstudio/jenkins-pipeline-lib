@Library('surf-lib@fversion-4.0.0-SNAPSHOT') // https://gitlab.com/surfstudio/infrastructure/tools/jenkins-pipeline-lib //todo change version to snapshot
import ru.surfstudio.ci.pipeline.pr.PrPipelineFlutter
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new PrPipelineFlutter(this)
pipeline.init()

//customization
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE

//run
pipeline.run()

@Library('surf-lib@version-2.0.0-SNAPSHOT') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/
import ru.surfstudio.ci.pipeline.pr.PrPipelineiOS
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new PrPipelineiOS(this)
pipeline.init()

//customization
pipeline.getStage(pipeline.UNIT_TEST).strategy = StageStrategy.SKIP_STAGE
pipeline.getStage(pipeline.INSTRUMENTATION_TEST).strategy = StageStrategy.SKIP_STAGE

//run
pipeline.run()

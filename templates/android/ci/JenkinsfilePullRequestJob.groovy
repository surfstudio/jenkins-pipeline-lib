@Library('surf-lib@version-2.0.0-SNAPSHOT') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/
import ru.surfstudio.ci.pipeline.pr.PrPipelineAndroid
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new PrPipelineAndroid(this)
pipeline.init()

//customization
pipeline.getStage(pipeline.INSTRUMENTATION_TEST).strategy = StageStrategy.SKIP_STAGE
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE
pipeline.getStage(pipeline.CODE_STYLE_FORMATTING).strategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR
pipeline.getStage(pipeline.UPDATE_CURRENT_COMMIT_HASH_AFTER_FORMAT).strategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR

//run
pipeline.run()

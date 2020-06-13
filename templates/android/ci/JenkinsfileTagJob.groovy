@Library('surf-lib@version-3.0.0-SNAPSHOT') // https://gitlab.com/surfstudio/infrastructure/tools/jenkins-pipeline-lib
import ru.surfstudio.ci.pipeline.tag.TagPipelineAndroid
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new TagPipelineAndroid(this)
pipeline.init()

//configuration
//TODO: set real values ↓↓↓ (mechanism see in AndroidUtil.withKeystore)
pipeline.keystoreCredentials = null
pipeline.keystorePropertiesCredentials = null

//customization
pipeline.getStage(pipeline.INSTRUMENTATION_TEST).strategy = StageStrategy.SKIP_STAGE
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE

//run
pipeline.run()
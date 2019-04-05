@Library('surf-lib@version-1.0.0-SNAPSHOT') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/
import ru.surfstudio.ci.pipeline.tag.TagPipelineFlutter
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new TagPipelineFlutter(this)
pipeline.init()

//configuration
//TODO: set real values ↓↓↓ (mechanism see in AndroidUtil.withKeystore)
pipeline.keystoreCredentials = null
pipeline.keystorePropertiesCredentials = null

//customization
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE

//run
pipeline.run()
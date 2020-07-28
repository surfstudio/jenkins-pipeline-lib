@Library('surf-lib@version-4.0.0-SNAPSHOT') // https://gitlab.com/surfstudio/infrastructure/tools/jenkins-pipeline-lib //todo change version to snapshot
import ru.surfstudio.ci.pipeline.tag.TagPipelineFlutter
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new TagPipelineFlutter(this)
pipeline.init()

//configuration
//TODO: set real values ↓↓↓ (mechanism see in AndroidUtil.withKeystore)
pipeline.androidKeystoreCredentials = null
pipeline.androidKeystorePropertiesCredentials = null

//customization
pipeline.getStage(pipeline.STATIC_CODE_ANALYSIS).strategy = StageStrategy.SKIP_STAGE

//run
pipeline.run()
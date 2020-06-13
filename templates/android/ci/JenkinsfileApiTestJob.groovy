@Library('surf-lib@version-3.0.0-SNAPSHOT') // https://gitlab.com/surfstudio/infrastructure/tools/jenkins-pipeline-lib
import ru.surfstudio.ci.pipeline.api_test.ApiTestPipelineAndroid
import ru.surfstudio.ci.stage.StageStrategy

//init
def pipeline = new ApiTestPipelineAndroid(this)
pipeline.init()

//run
pipeline.run()
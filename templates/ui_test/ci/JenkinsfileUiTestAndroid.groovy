@Library('surf-lib') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/
import ru.surfstudio.ci.pipeline.UiTestPipelineAndroid

//init
def pipeline = new UiTestPipelineAndroid(this)
pipeline.init()

//customization

//run
pipeline.run()

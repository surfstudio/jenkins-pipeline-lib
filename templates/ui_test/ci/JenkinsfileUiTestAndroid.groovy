@Library('surf-lib@version-3.0.0-SNAPSHOT') // https://gitlab.com/surfstudio/infrastructure/tools/jenkins-pipeline-lib
import ru.surfstudio.ci.pipeline.ui_test.UiTestPipelineAndroid

//init
def pipeline = new UiTestPipelineAndroid(this)
pipeline.init()

//configuration
//TODO: set real values ↓↓↓
pipeline.sourceRepoUrl = "" //repository with app source code
pipeline.jiraProjectKey = ""
pipeline.defaultTaskKey = "" //task for run periodically
pipeline.testBranch = "master" // branch with tests

//customization

//run
pipeline.run()

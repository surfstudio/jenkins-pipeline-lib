@Library('surf-lib@version-1.0.0-SNAPSHOT') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/
import ru.surfstudio.ci.pipeline.ui_test.UiTestPipelineAndroid

//init
def pipeline = new UiTestPipelineAndroid(this)
pipeline.init()

//configuration
//TODO: set real values ↓↓↓
pipeline.sourceRepoUrl = "" //repository with app source code
pipeline.jiraProjectKey = ""
pipeline.testBranch = "" // branch with tests
pipeline.defaultTaskKey = "" //task for run periodically

//customization

//run
pipeline.run()

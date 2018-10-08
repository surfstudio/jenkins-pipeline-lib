@Library('surf-lib@version-1.0.0-SNAPSHOT') // https://bitbucket.org/surfstudio/jenkins-pipeline-lib/ 
import ru.surfstudio.ci.pipeline.empty.EmptyScmPipeline
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.Result
import static CommonUtil.encodeUrl

//init
def pipeline = new EmptyScmPipeline(this)

//configuration
def mirrorRepoCredentialID = "76dbac13-e6ea-4ed0-a013-e06cad01be2d"

pipeline.node = NodeProvider.getWorkerNode()

pipeline.propertiesProvider = {
    return [
            pipelineTriggers([
                    GenericTrigger(),
                    pollSCM('')
            ])
    ]
}

//stages
pipeline.stages = [
        pipeline.createStage("Clone", StageStrategy.FAIL_WHEN_STAGE_ERROR) {
            sh "rm -rf jenkins-pipeline-lib.git"
            withCredentials([usernamePassword(credentialsId: pipeline.repoCredentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                echo "credentialsId: $pipeline.repoCredentialsId"
                sh "git clone --mirror https://${encodeUrl(USERNAME)}:${encodeUrl(PASSWORD)}@bitbucket.org/surfstudio/jenkins-pipeline-lib.git"
            }
        },
        pipeline.createStage("Mirroing", StageStrategy.FAIL_WHEN_STAGE_ERROR) {
            dir("jenkins-pipeline-lib.git") {
                withCredentials([usernamePassword(credentialsId: mirrorRepoCredentialID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    echo "credentialsId: $mirrorRepoCredentialID"
                    sh "git push --mirror https://${encodeUrl(USERNAME)}:${encodeUrl(PASSWORD)}@github.com/surfstudio/jenkins-pipeline-lib.git"
                }
            }
        }
]

pipeline.finalizeBody = {
    if (pipeline.jobResult == Result.FAILURE) {
        def message = "Ошибка зеркалирования Jenkins Pipeline Lib на GitHub. ${CommonUtil.getBuildUrlMarkdownLink(this)}"
        JarvisUtil.sendMessageToGroup(this, message, pipeline.repoUrl, "bitbucket", false)
    }
}

//run
pipeline.run()
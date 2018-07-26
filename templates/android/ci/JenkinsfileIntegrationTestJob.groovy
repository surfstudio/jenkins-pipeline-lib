@Library('surf-lib')
import ru.surfstudio.ci.pipeline.EmptyPipeline
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider

import static ru.surfstudio.ci.CommonUtil.applyParameterIfNotEmpty

//Кастомный пайплайн для прогона Интеграционных тестов

//init
def pipeline = new EmptyPipeline(this)
def branchName = ""
pipeline.init()

//configuration
pipeline.node = NodeProvider.getAndroidNode()

pipeline.preExecuteStageBody = {}
pipeline.postExecuteStageBody = {}

pipeline.stages = [
        pipeline.createStage("Init", StageStrategy.FAIL_WHEN_STAGE_ERROR){

            applyParameterIfNotEmpty(this, 'branchName', this.params.branchName, {
                value -> branchName = value
            })

            if(!branchName){
                branchName = JarvisUtil.getMainBranch(this, this.scm.userRemoteConfigs[0].url)
                this.echo "Using Main Branch: ${branchName}"
            }

            CommonUtil.tryAbortOlderBuildsWithDescription(this, branchName)
        },
        pipeline.createStage("Checkout", StageStrategy.FAIL_WHEN_STAGE_ERROR){
            this.checkout([
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${branchName}"]],
                    doGenerateSubmoduleConfigurations: this.scm.doGenerateSubmoduleConfigurations,
                    userRemoteConfigs                : this.scm.userRemoteConfigs,
            ])
        },
        pipeline.createStage("IntegrationTest", StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
            CommonAndroidStages.instrumentationTestStageBodyAndroid(this,
                    "connectedAndroidTest",
                    "**/outputs/androidTest-results/connected/*.xml",
                    "app/build/reports/androidTests/connected/")
        }
]

pipeline.finalizeBody = {
    def jenkinsLink = CommonUtil.getBuildUrlHtmlLink(this)
    def message
    def success = pipeline.jobResult != Result.SUCCESS
    if (success) {
        def unsuccessReasons = CommonUtil.unsuccessReasonsToString(pipeline.stages)
        message = "Интеграционные тесты не выполнены из-за этапов: ${unsuccessReasons}. ${jenkinsLink}"
    } else {
        message = "Интеграционные тесты выполнены"
    }
    JarvisUtil.sendMessageToGroup(this, message, this.scm.userRemoteConfigs[0].url, "bitbucket", success)
}

//run
pipeline.run()
package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.CommonUtil

class CommoniOSStages {

    def static buildStageBodyAndroid(Object script) {
        script.sh "make init"
        script.sh "make build"
    }

    def static unitTestStageBodyAndroid(Object script) {
        script.echo "empty"
        // TODO: Implement me
    }

    def static instrumentationTestStageBodyAndroid(Object script) {
        script.echo "empty"
        // TODO: Implement me
    }

    def static staticCodeAnalysisStageBody(Object script) {
        script.sh "make sonar"
    }


}
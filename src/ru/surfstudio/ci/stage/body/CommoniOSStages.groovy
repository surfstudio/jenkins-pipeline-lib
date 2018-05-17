package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.CommonUtil

class CommoniOSStages {

    def static buildStageBodyiOS(Object script) {
        script.sh "make init"
        script.sh "make build"
    }

    def static unitTestStageBodyiOS(Object script) {
        script.echo "empty"
        // TODO: Implement me
    }

    def static instrumentationTestStageBodyiOS(Object script) {
        script.echo "empty"
        // TODO: Implement me
    }

    def static staticCodeAnalysisStageBodyiOS(Object script) {
        script.sh "make sonar"
    }


}
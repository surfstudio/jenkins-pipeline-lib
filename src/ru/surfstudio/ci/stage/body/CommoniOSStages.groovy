package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.CommonUtil

class CommoniOSStages {

    def static buildStageBodyiOS(Object script) {
        withCredentials([
            string(credentialsId: 'Jenkins keychain pass', variable: 'KEYCHAINPASS'),
            file(credentialsId: 'iOSDevIvanSmetaninVM.p12', variable: 'DEVELOPER_P12_KEY')
        ]) {

            script.sh "security -v unlock-keychain -p $KEYCHAINPASS"
            script.sh "security import \"$DEVELOPER_P12_KEY\" -P \"\""
            
            script.sh "make init"
            script.sh "make build"
        }
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
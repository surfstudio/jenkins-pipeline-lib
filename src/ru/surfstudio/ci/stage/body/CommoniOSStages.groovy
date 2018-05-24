package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.CommonUtil

class CommoniOSStages {

    def static buildStageBodyiOS(Object script) {
        script.withCredentials([
            script.string(credentialsId: 'add420b4-78fc-4db0-95e9-eeb0eac780f6', variable: 'KEYCHAIN_PASS'),
            script.file(credentialsId: 'IvanSmetanin_iOS_Dev_CertKey', variable: 'DEVELOPER_P12_KEY')
        ]) {

            script.sh "security -v unlock-keychain -p $KEYCHAIN_PASS"
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
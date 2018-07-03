package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.CommonUtil

class CommoniOSStages {

    def static buildStageBodyiOS(Object script, String keychainCredenialId, String certfileCredentialId) {
        script.withCredentials([
            script.string(credentialsId: keychainCredenialId, variable: 'KEYCHAIN_PASS'),
            script.file(credentialsId: certfileCredentialId, variable: 'DEVELOPER_P12_KEY')
        ]) {

            script.sh 'security -v unlock-keychain -p $KEYCHAIN_PASS'
            script.sh 'security import "$DEVELOPER_P12_KEY" -P ""'
            
            CommonUtil.shWithRuby(script, "make init")
            CommonUtil.shWithRuby(script, "make build")
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
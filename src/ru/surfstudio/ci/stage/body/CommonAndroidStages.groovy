package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.AndroidUtil
import ru.surfstudio.ci.CommonUtil

class CommonAndroidStages {

    def static buildStageBodyAndroid(Object script, String buildGradleTask) {
        script.sh "./gradlew ${buildGradleTask}"
        script.step([$class: 'ArtifactArchiver', artifacts: '**/*.apk'])
        CommonUtil.safe(script) {
            script.step([$class: 'ArtifactArchiver', artifacts: '**/mapping.txt'])
        }
    }

    def static unitTestStageBodyAndroid(Object script, String unitTestGradleTask, String testResultPathXml, String testResultPathDirHtml) {
        try {
            script.sh "./gradlew ${unitTestGradleTask}"
        } finally {
            script.junit allowEmptyResults: true, testResults: testResultPathXml
            script.publishHTML(target: [
                    allowMissing         : true,
                    alwaysLinkToLastBuild: false,
                    keepAll              : true,
                    reportDir            : testResultPathDirHtml,
                    reportFiles          : 'index.html',
                    reportName           : "Unit Tests"
            ])
        }
    }

    def static instrumentationTestStageBodyAndroid(Object script, String testGradleTask, String testResultPathXml, String testResultPathDirHtml) {
        AndroidUtil.onEmulator(script, "avd-main"){
            try {
                script.sh "./gradlew uninstallAll ${testGradleTask}"
            } finally {
                script.junit allowEmptyResults: true, testResults: testResultPathXml
                script.publishHTML(target: [allowMissing         : true,
                                     alwaysLinkToLastBuild: false,
                                     keepAll              : true,
                                     reportDir            : testResultPathDirHtml,
                                     reportFiles          : 'index.html',
                                     reportName           : "Instrumental Tests"
                ])
            }
        }
    }

    def static staticCodeAnalysisStageBody(Object script){
        script.echo "empty"
        //todo
    }


}
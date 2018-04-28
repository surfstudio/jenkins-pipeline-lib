package ru.surfstudio.ci.stage.body

import ru.surfstudio.ci.AndroidUtil

class CommonAndroidStages {

    def static buildStageAndroidBody(Object script, String buildGradleTask) {
        script.sh "./gradlew clean ${buildGradleTask}"
        script.step([$class: 'ArtifactArchiver', artifacts: '**/*.apk'])
        script.step([$class: 'ArtifactArchiver', artifacts: '**/mapping.txt'])
    }

    def static unitTestStageAndroidBody(Object script, String unitTestGradleTask, String testResultPathXml, String testResultPathDirHtml) {
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

    def static instrumentationTestStageAndroidBody(Object script, String testGradleTask, String testResultPathXml, String testResultPathDirHtml) {
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
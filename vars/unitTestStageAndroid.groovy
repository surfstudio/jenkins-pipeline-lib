import ru.surfstudio.ci.Pipeline

def static call(Pipeline ctx, String unitTestGradleTask, String testResultPathXml, String testResultPathDirHtml) {
    try {
        ctx.origin.sh "./gradlew ${unitTestGradleTask}"
    } finally {
        ctx.origin.junit allowEmptyResults: true, testResults: testResultPathXml
        ctx.origin.publishHTML(target: [
                allowMissing         : true,
                alwaysLinkToLastBuild: false,
                keepAll              : true,
                reportDir            : testResultPathDirHtml,
                reportFiles          : 'index.html',
                reportName           : "Unit Tests"
        ])
    }
}
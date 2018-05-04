package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

abstract class UiTestPipeline extends BitbucketPipeline {
    public sourcesDir = "sources"
    public featuresDir = "features"
    public outputsDir = "outputs"

    public featureForTest = "for_test.feature"
    public outputJsonFile = "report.json"
    public outputHtmlFile = "report.html"

    public jiraAuthenticationName = 'Jarvis_Jira'

    //scm
    public sourceBranch = ""
    public sourceRepoUrl = ""
    public testBranch = ""

    //jira
    public taskKey = ""
    public taskName = ""
    public userEmail = ""

    UiTestPipeline(Object script) {
        super(script)
    }
}

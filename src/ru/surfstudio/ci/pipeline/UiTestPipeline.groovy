package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

class UiTestPipeline extends Pipeline {
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

    //default stage strategies
    public checkoutSourcesStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public checkoutTestsStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public buildStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public prepareArtifactStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public prepareTestStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public testStageStrategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR
    public publishResultsStageStrategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR


    UiTestPipeline(Object script) {
        super(script)
    }
}

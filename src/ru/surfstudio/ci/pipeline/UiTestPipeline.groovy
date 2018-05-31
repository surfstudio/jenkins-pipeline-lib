package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

abstract class UiTestPipeline extends Pipeline {

    //stage names
    public static final String INIT = 'Init'
    public static final String CHECKOUT_SOURCES = 'Checkout Sources'
    public static final String CHECKOUT_TESTS = 'Checkout Tests'
    public static final String BUILD = 'Build'
    public static final String PREPARE_ARTIFACT = 'Prepare Artifact'
    public static final String PREPARE_TESTS = 'Prepare Tests'
    public static final String TEST = 'Test'
    public static final String PUBLISH_RESULTS = 'Publish Results'

    //dirs
    public sourcesDir = "sources"
    public featuresDir = "features"
    public outputsDir = "outputs"

    //files
    public featureForTest = "for_test.feature"
    public outputJsonFile = "report.json"
    public outputHtmlFile = "report.html"

    //credentials
    public jiraAuthenticationName = 'Jarvis_Jira'

    //scm
    public sourceBranch = ""
    public sourceRepoUrl = ""
    public testBranch = ""

    //jira
    public taskKey = ""
    public taskName = ""
    public userEmail = ""

    //notification
    public notificationEnabled = true

    //ios
    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    UiTestPipeline(Object script) {
        super(script)
    }
}

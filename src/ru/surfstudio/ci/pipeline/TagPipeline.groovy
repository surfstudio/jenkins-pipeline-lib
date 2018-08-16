package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.Constants
import ru.surfstudio.ci.stage.StageStrategy

abstract class TagPipeline extends AutoAbortedPipeline {

    //stage names
    public static final String CHECKOUT = 'Checkout'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'
    public static final String BETA_UPLOAD = 'Beta Upload'

    //required configuration
    public repoUrl = ""
    public repoCredentialsId = Constants.BITBUCKET_BUILDER_CREDENTIALS_ID

    //scm
    public repoTag = ""

    TagPipeline(Object script) {
        super(script)
    }

    @Override
    String getBuildIdentifier() {
        return repoTag
    }
}
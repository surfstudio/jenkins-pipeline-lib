#!/usr/bin/groovy
package ru.surfstudio.ci

import ru.surfstudio.ci.*

class PrContext extends BaseContext {

    //strategies
    public preMergeStageStrategy = FAIL_WHEN_STAGE_ERROR
    public buildStageStrategy = FAIL_WHEN_STAGE_ERROR
    public unitTestStageStrategy = UNSTABLE_WHEN_STAGE_ERROR
    public smallInstrumentationTestStageStrategy = UNSTABLE_WHEN_STAGE_ERROR
    public staticCodeAnalysisStageStrategy = UNSTABLE_WHEN_STAGE_ERROR

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""

    PrContext(Object script) {
        super(script)
    }
}
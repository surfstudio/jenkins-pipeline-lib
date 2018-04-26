#!/usr/bin/groovy
package ru.surfstudio.ci

import ru.surfstudio.ci.*

class PrContext extends BaseContext {

    //strategies
    public preMergeStageStrategy = StageStartegy.FAIL_WHEN_STAGE_ERROR
    public buildStageStrategy = StageStartegy.FAIL_WHEN_STAGE_ERROR
    public unitTestStageStrategy = StageStartegy.UNSTABLE_WHEN_STAGE_ERROR
    public smallInstrumentationTestStageStrategy = StageStartegy.UNSTABLE_WHEN_STAGE_ERROR
    public staticCodeAnalysisStageStrategy = StageStartegy.UNSTABLE_WHEN_STAGE_ERROR

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""

    PrContext(Object script) {
        super(script)
    }
}
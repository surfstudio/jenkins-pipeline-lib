package ru.surfstudio.ci

import ru.surfstudio.ci.StageStrategy

class PrContext extends BaseContext implements Serializable {

    //strategies
    public preMergeStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    /*public buildStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public unitTestStageStrategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR
    public smallInstrumentationTestStageStrategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR
    public staticCodeAnalysisStageStrategy = StageStrategy.UNSTABLE_WHEN_STAGE_ERROR*/

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""

    PrContext(Object script) {
        super(script)
    }
}
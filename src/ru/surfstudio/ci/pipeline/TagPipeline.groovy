package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

abstract class TagPipeline extends Pipeline {

    //strategies
    public checkoutStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public buildStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public unitTestStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public smallInstrumentationTestStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public staticCodeAnalysisStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
    public betaUploadStageStrategy = StageStrategy.FAIL_WHEN_STAGE_ERROR

    public repoTag = ""

    TagPipeline(Object script) {
        super(script)
    }
}
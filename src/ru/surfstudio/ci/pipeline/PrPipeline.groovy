package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

abstract class PrPipeline extends Pipeline {

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""

    PrPipeline(Object script) {
        super(script)
    }
}
package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

abstract class TagPipeline extends Pipeline {

    public repoTag = ""

    TagPipeline(Object script) {
        super(script)
    }
}
package ru.surfstudio.ci.pipeline.empty

import ru.surfstudio.ci.pipeline.ScmPipeline

class EmptyScmPipeline extends ScmPipeline {

    def Closure<String> buildIdentifierProvider

    EmptyScmPipeline(Object script) {
        super(script)
    }

    @Override
    def init() {
        //empty
    }

    @Override
    def initInternal() {

    }
}

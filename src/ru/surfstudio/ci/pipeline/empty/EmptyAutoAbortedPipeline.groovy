package ru.surfstudio.ci.pipeline.empty

import ru.surfstudio.ci.pipeline.base.AutoAbortedPipeline

class EmptyAutoAbortedPipeline extends AutoAbortedPipeline {

    def Closure<String> buildIdentifierProvider

    EmptyAutoAbortedPipeline(Object script) {
        super(script)
    }

    @Override
    def initInternal() {
        //empty
    }

    @Override
    String getBuildIdentifier() {
        if(buildIdentifierProvider) {
            return buildIdentifierProvider()
        } else {
            script.error "You must specify field EmptyAutoAbortedPipeline.buildIdentifierProvider"
        }

    }
}

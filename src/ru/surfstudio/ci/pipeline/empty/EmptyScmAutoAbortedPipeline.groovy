package ru.surfstudio.ci.pipeline.empty

import ru.surfstudio.ci.pipeline.ScmAutoAbortedPipeline

class EmptyScmAutoAbortedPipeline extends ScmAutoAbortedPipeline {

    def Closure<String> buildIdentifierProvider

    EmptyScmAutoAbortedPipeline(Object script) {
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

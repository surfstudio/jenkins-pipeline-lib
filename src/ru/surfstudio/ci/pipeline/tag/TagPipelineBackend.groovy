package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.NodeProvider

class TagPipelineBackend extends TagPipeline {
    public buildGradleTask = "clean assemble"
    public registryPathAndProjectId = ""

    TagPipelineBackend(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.backendNode
        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
        ]
        finalizeBody = { finalizeStageBody(this) }
    }
}
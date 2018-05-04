package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Result

/**
 * Базовый pipeline с оповещением bitbucket о каждом Stage
 */
class BitbucketPipeline extends Pipeline {

    BitbucketPipeline(Object script) {
        super(script)
    }

    @Override
    def init() {
        preExecuteStageBody = { stage -> CommonUtil.notifyBitbucketAboutStageStart(script, stage.name) }
        postExecuteStageBody = { stage -> CommonUtil.notifyBitbucketAboutStageFinish(script, stage.name, stage.result == Result.SUCCESS)}
    }
}
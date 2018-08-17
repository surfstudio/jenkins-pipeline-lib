package ru.surfstudio.ci.pipeline.empty

import ru.surfstudio.ci.pipeline.base.Pipeline

/**
 * Предназначен для конфигурации кастомных скриптов на основе механизмов класса {@link ru.surfstudio.ci.pipeline.base.Pipeline}
 */
class EmptyPipeline extends Pipeline {

    EmptyPipeline(Object script) {
        super(script)
    }

    @Override
    def init() {
        //empty
    }
}

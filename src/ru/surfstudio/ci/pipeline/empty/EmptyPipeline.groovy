package ru.surfstudio.ci.pipeline.empty

import ru.surfstudio.ci.pipeline.Pipeline

/**
 * Предназначен для конфигурации кастомных скриптов на основе механизмов класса {@link Pipeline}
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

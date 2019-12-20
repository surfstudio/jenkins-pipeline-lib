package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline;

/**
 * Base stage for {@link Pipeline}
 * Can be wrapper over other stagea
 */
interface Stage {

    String getName()

    void execute(Object script, Pipeline context)

}


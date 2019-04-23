package ru.surfstudio.ci.stage

import ru.surfstudio.ci.pipeline.Pipeline;

/**
 * базовый интерфейс для всех стейждей, в том числе для комплексных
 * Наследники могут как содержать так и не содержть оригинальных стейджей дженкинса
 */
public interface Stage {

    String getName()

    void execute(Object script, Pipeline context)

}


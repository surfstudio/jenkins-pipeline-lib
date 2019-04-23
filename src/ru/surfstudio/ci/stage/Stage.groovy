package ru.surfstudio.ci.stage;

/**
 * базовый интерфейс для всех стейждей, в том числе для комплексных
 * Наследники могут как содержать так и не содержть оригинальных стейджей дженкинса
 */
public interface Stage {

    String getName()

    void execute(Object script, Closure preExecuteStageBody, Closure postExecuteStageBody)

}


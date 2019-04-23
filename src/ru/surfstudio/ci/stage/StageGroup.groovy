package ru.surfstudio.ci.stage

/**
 *  Group if {@link Stage}
 */
interface StageGroup extends Stage {
    List<Stage> getStages()

}
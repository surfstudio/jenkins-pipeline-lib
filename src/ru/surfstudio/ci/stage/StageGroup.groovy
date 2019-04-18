package ru.surfstudio.ci.stage

interface StageGroup extends Stage {
    List<Stage> getStages()

}
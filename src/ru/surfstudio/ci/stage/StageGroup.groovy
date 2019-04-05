package ru.surfstudio.ci.stage

interface StageGroup extends StageInterface {
    List<StageInterface> getStages()

}
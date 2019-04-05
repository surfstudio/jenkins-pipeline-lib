package ru.surfstudio.ci.utils

import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageGroup
import ru.surfstudio.ci.stage.StageInterface

class StageTreeUtil {

    /**
     * Replace stage with same name as newStage
     * @param newStage
     * @param stagesTree - tree of stagesTree
     * @return true/false
     */
    def static replaceStage(List<StageInterface> stagesTree, Stage newStage) {
        for(int i = 0; i < stagesTree.size(); i++){
            def stage = stagesTree.get(i)
            if(stage.name == newStage.name) {
                stagesTree.remove(i)
                stagesTree.add(i, newStage)
                return true
            } else if (stage instanceof StageGroup){
                def result = replaceStage(stage.stages, newStage)
                if (result) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Get stage by name
     * @param stageName
     * @param stagesTree - tree of stagesTree
     * @return stage/null
     */
    def static getStage(List<StageInterface> stagesTree, String stageName) {
        for(StageInterface stage in stagesTree){
            if(stage.name == stageName) {
                return stage
            } else if (stage instanceof StageGroup){
                def result = getStage(stage.stages, stageName)
                if (result) {
                    return result
                }
            }
        }
        return null
    }

    /**
     * execute lambda with all stagesTree in stage three
     * @param lambda: { stage ->  ... }
     * @param stagesTree - tree of stagesTree
     */
    def static forStages(List<StageInterface> stagesTree, Closure lambda) {
        for(StageInterface stage in stagesTree){
            lambda(stage)
            if (stage instanceof StageGroup){
                forStages(stage.stages, lambda)
            }
        }
        return null
    }
}

#!/usr/bin/groovy
/*
  Copyright (c) 2018-present, SurfStudio LLC.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.stage.NodeStagesWrapper
import ru.surfstudio.ci.stage.ParallelStageSet
import ru.surfstudio.ci.stage.SimpleStage
import ru.surfstudio.ci.stage.StageGroup
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.StageTreeUtil

/**
 * Наследники класса Pipeline - ключевые сущности для выполнения скрипта
 * По сути знают что и как выполнять и определяют параметры выполнения
 *  - "как" определяется методом run (не нужно переопределять)
 *  - "что" определяется в методе init (нужно переопределять)
 *  - "параметры выполнения" определяются через публичные переменные
 *
 *  Для создания собственного наследника необходимо переопределить метод init и в нем определенить переменные
 *  (Любая из них может быть не проинициализирована):
 *  - node                  :  Машина, на которой будет выполняться основная работа(stages)
 *  - stages                :  Массив с обьектами определяющими блоки основной работы
 *  - initializeBody        :  Лямбда, котороая будет выполняться перед стартом основной работы на master машине
 *  - finalizeBody          :  Лямбда, которая будет выполняться после основной работы, как в случае успешного завершения, так и после ошибки
 *  - propertiesProvider    :  Лямбда, которая должна вернуть массив properties, например триггеры и параметры сборки
 *  - preExecuteStageBody   :  Лямбда, которая выполняется до выполнения Stage
 *  - postExecuteStageBody  :  Лямбда, которая выполняется после выполнения Stage
 *
 *  Предусмотрены различные способы кастомизации
 *  - изменение переменных, определяющих контекст
 *  - изменение стратегии/тела Stage (для получения stage следует использовать getStage())
 *  - замена целых Stage через метод replaceStage() или напрямую через переменную stages
 *  - все остальное, что может прийти в голову, так как все переменные публичные
 *
 *  Большую часть деталей реализации следует размешать в классах ...Util для возможности переиспользования
 *  без механизмов класса Pipeline
 */
abstract class Pipeline implements Serializable {
    public static final String INIT = 'Init'
    public static final String DEFAULT_STAGE_STRATEGY = StageStrategy.FAIL_WHEN_STAGE_ERROR

    public script //Jenkins Pipeline Script
    public jobResult = Result.NOT_BUILT
    public node
    public Closure initializeBody  //runs on master node
    public Closure<List<Object>> propertiesProvider  //runs after initializeBody on master node
    public List<Stage> stages     //runs on specified node
    public Closure finalizeBody

    public preExecuteStageBody = {}  // { stage -> ... } runs for all stages in 'stages' list
    public postExecuteStageBody = {} // { stage -> ... } runs for all stages in 'stages' list

    Pipeline(script) {
        this.script = script
    }

    /**
     * Инициализацию пайплайна нужно проводить здесь вместо конструктора из-за особенностей рантайм выполенния
     * https://issues.jenkins-ci.org/browse/JENKINS-26313
     * Этот метод нужно вызвать после вызова конструктора
     */
    abstract def init()

    def run() {
        CommonUtil.fixVisualizingStagesInParallelBlock(script)
        try {
            def initStage = createStage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR, createInitStageBody())
            initStage.execute(script, {}, {})
            script.node(node) {
                if(CommonUtil.notEmpty(node)) {
                    script.echo "Switch to node ${node}: ${script.env.NODE_NAME}"
                }
                for (Stage stage : stages) {
                    stage.execute(script, preExecuteStageBody, postExecuteStageBody)
                }
            }
        }  finally {
            jobResult = calculateJobResult(stages)
            if (jobResult == Result.ABORTED || jobResult == Result.FAILURE) {
                script.echo "Job stopped, see reason above ^^^^"
            }
            script.echo "Finalize build:"
            printStageResults()
            script.echo "Current job result: ${script.currentBuild.result}"
            script.echo "Try apply job result: ${jobResult}"
            script.currentBuild.result = jobResult  //нельзя повышать статус, то есть если раньше был установлен failed или unstable, нельзя заменить на success
            script.echo "Updated job result: ${script.currentBuild.result}"
            if (finalizeBody) {
                script.echo "Start finalize body"
                finalizeBody()
                script.echo "End finalize body"
            }
        }
    }


    /**
     * Replace stage with same name as newStage
     * @param newStage
     * @return true/false
     */
    def replaceStage(SimpleStage newStage) {
        return StageTreeUtil.replaceStage(stages, newStage)
    }

    /**
     * Get stage by name
     * @param stageName
     * @return stage/null
     */
    def getStage(String stageName) {
        return StageTreeUtil.getStage(stages, stageName)
    }

    /**
     * execute lambda with all stages in stage tree
     * @param lambda: { stage ->  ... }
     */
    def forStages(Closure lambda) {
        return StageTreeUtil.forStages(stages, lambda)
    }

    // ==================================== DSL =========================================

    // ------------ Simple Stage ----------------

    /**
     * run simple stage
     */
    def static stage(String name, String strategy, Closure body){
        return new SimpleStage(name, strategy, body)
    }

    /**
     * run simple stage with {@link #DEFAULT_STAGE_STRATEGY}
     */
    def static stage(String name, Closure body) {
        return new SimpleStage(name, DEFAULT_STAGE_STRATEGY, body)
    }

    // ------------ Parallel ----------------

    /**
     * Run Stages in parallel
     */
    def static parallel(List<SimpleStage> stages) {
        return new ParallelStageSet("Parallel", false, stages)
    }

    def static parallel(String name, List<SimpleStage> stages) {
        return new ParallelStageSet(name, false, stages)
    }

    def static parallel(boolean copyWorkspace, List<SimpleStage> stages) {
        return new ParallelStageSet("Parallel", copyWorkspace, stages)
    }

    def static parallel(String name, boolean copyWorkspace, List<SimpleStage> stages) {
        return new ParallelStageSet(name, copyWorkspace, stages)
    }

    // ------------ Node ----------------

    /**
     * Run stages on specific node
     */
    def static node(String node, boolean copyWorkspace, List<SimpleStage> stages) {
        return new NodeStagesWrapper("Node: $node", node, copyWorkspace, stages)
    }

    /**
     * Run stages on specific node.
     */
    def static node(String node, List<SimpleStage> stages) {
        return new NodeStagesWrapper("Node: $node", node, false, stages)
    }

    // ==================================== UTIL =========================================

    def printStageResults() {
        forStages {stage ->
            if(stage instanceof SimpleStage) {
                script.echo(String.format("%-30s", "\"${stage.name}\" stage result: ") + stage.result)
            }
        }
    }

    def Closure createInitStageBody() {
        return {
            if (initializeBody) initializeBody()
            if (propertiesProvider) script.properties(propertiesProvider())
        }
    }

    def calculateJobResult(Collection<Stage> stages) {
        def jobResultPriority = [:]
        jobResultPriority[Result.ABORTED] = 5
        jobResultPriority[Result.FAILURE] = 4
        jobResultPriority[Result.UNSTABLE] = 3
        jobResultPriority[Result.SUCCESS] = 2
        jobResultPriority[Result.NOT_BUILT] = 1

        def currentJobResult = Result.NOT_BUILT
        for (Stage abstractStage : stages) {
            def newJobResult
            if(abstractStage instanceof StageGroup) {
                newJobResult = this.calculateJobResult((abstractStage as StageGroup).stages)
            } else if(abstractStage instanceof SimpleStage) {
                def stage = abstractStage as SimpleStage
                if (stage.result == null) {
                    newJobResult = Result.NOT_BUILT
                } else  if (stage.result == Result.SUCCESS) {
                    newJobResult = Result.SUCCESS
                } else if (stage.result == Result.ABORTED) {
                    newJobResult = Result.ABORTED
                } else if (stage.result == Result.NOT_BUILT) {
                    newJobResult = Result.NOT_BUILT
                } else if (stage.result == Result.FAILURE) {
                    if (stage.strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                        newJobResult = Result.FAILURE
                    } else if (stage.strategy == StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                        newJobResult = Result.UNSTABLE
                    } else if (stage.strategy == StageStrategy.SUCCESS_WHEN_STAGE_ERROR) {
                        newJobResult = Result.SUCCESS
                    }
                } else {
                    script.error("Unsupported stage result " + stage.result)
                }
            }
            if (jobResultPriority[newJobResult] > jobResultPriority[currentJobResult]) {
                currentJobResult = newJobResult
            }
        }
        return currentJobResult
    }

    // ================================== Deprecate ======================================

    @Deprecated
    def static createStage(String name, String strategy, Closure body){
        return new SimpleStage(name, strategy, body)
    }

    @Deprecated
    def stageWithStrategy(SimpleStage stage, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        stage.execute(script, preExecuteStageBody, postExecuteStageBody)
    }
}

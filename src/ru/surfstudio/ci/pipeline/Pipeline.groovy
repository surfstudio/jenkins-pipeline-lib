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

import ru.surfstudio.ci.Result
import ru.surfstudio.ci.stage.Stage
import ru.surfstudio.ci.stage.StageStrategy

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

    public script //Jenkins Pipeline Script
    public jobResult = Result.SUCCESS
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
        try {
            def initStage = createStage(INIT, StageStrategy.FAIL_WHEN_STAGE_ERROR, createInitStageBody())
            stageWithStrategy(initStage, {}, {})
            script.node(node) {
                for (Stage stage : stages) {
                    stageWithStrategy(stage, preExecuteStageBody, postExecuteStageBody)
                }
            }
        }  finally {
            script.echo "Finalize build:"
            script.echo "Current job result: ${script.currentBuild.result}"
            script.echo "Try apply job result: ${jobResult}"
            script.currentBuild.result = jobResult  //нельзя повышать статус, то есть если раньше был установлен failed, нельзя заменить на success
            script.echo "Updated job result: ${script.currentBuild.result}"
            if (finalizeBody) {
                script.echo "Start finalize body"
                finalizeBody()
                script.echo "End finalize body"
            }
        }
    }

    def Closure createInitStageBody() {
        return {
            if (initializeBody) initializeBody()
            if (propertiesProvider) script.properties(propertiesProvider())
        }
    }

    def stageWithStrategy(Stage stage, Closure preExecuteStageBody, Closure postExecuteStageBody) {
        //https://issues.jenkins-ci.org/browse/JENKINS-39203 подождем пока сделают разные статусы на разные Stage
        script.stage(stage.name) {
            if (stage.strategy == StageStrategy.SKIP_STAGE) {
                return
            } else {
                try {
                    script.echo("Stage ${stage.name} started")
                    if(preExecuteStageBody){
                        preExecuteStageBody(stage)
                    }
                    stage.body()
                    stage.result = Result.SUCCESS
                    script.echo("Stage ${stage.name} success")
                } catch (e) {
                    script.echo "Error: ${e.toString()}"
                    if(e.getCause()!=null) {
                        script.echo "Cause error: ${e.getCause().toString()}"
                    }

                    if(e instanceof InterruptedException || //отменено из другого процесса
                            e instanceof hudson.AbortException && e.getMessage() == "script returned exit code 143") { //отменено пользователем
                        script.echo("Stage ${stage.name} aborted")
                        stage.result = Result.ABORTED
                        jobResult = Result.ABORTED
                        throw e
                    } else {
                        script.echo("Stage ${stage.name} fail")
                        script.echo("Apply stage strategy: ${stage.strategy}")
                        if (stage.strategy == StageStrategy.FAIL_WHEN_STAGE_ERROR) {
                            stage.result = Result.FAILURE
                            jobResult = Result.FAILURE
                            throw e
                        } else if (stage.strategy == StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                            stage.result = Result.UNSTABLE
                            if (jobResult != Result.FAILURE) {
                                jobResult = Result.UNSTABLE
                            }
                        } else if (stage.strategy == StageStrategy.SUCCESS_WHEN_STAGE_ERROR) {
                            stage.result = Result.SUCCESS
                        } else {
                            script.error("Unsupported strategy " + stage.strategy)
                        }
                    }
                } finally {
                    if(postExecuteStageBody){
                        postExecuteStageBody(stage)
                    }
                    if(jobResult == Result.ABORTED || jobResult == Result.FAILURE) {
                        script.echo "Job stopped, see reason above ^^^^"
                    }
                }
            }
        }
    }

    def replaceStage(Stage newStage) {
        for(int i = 0; i < stages.size(); i++){
            if(stages.get(i).name == newStage.name) {
                stages.remove(i)
                stages.add(i, newStage)
                return
            }
        }
    }

    def getStage(String stageName) {
        for(Stage stage in  stages){
            if(stage.name == stageName) {
                return stage
            }
        }
        script.error("stage with name ${stageName} doesn't exist in pipeline")
    }

    def static createStage(String name, String strategy, Closure body){
        return new Stage(name, strategy, body)
    }
}

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
package ru.surfstudio.ci.pipeline.pr

import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.pipeline.helper.AndroidPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.android.config.AndroidTestConfig
import ru.surfstudio.ci.utils.android.config.AvdConfig

class PrPipelineAndroid extends PrPipeline {

    //required initial configuration
    public keystoreCredentials = "no_credentials"
    public keystorePropertiesCredentials = "no_credentials"

    public buildGradleTask = "clean assembleQa"

    public unitTestGradleTask = "testQaUnitTest -PtestType=unit"
    public unitTestResultPathXml = "**/test-results/testQaUnitTest/*.xml"
    public unitTestResultPathDirHtml = "app/build/reports/tests/testQaUnitTest/"

    // buildType, для которого будут выполняться инструментальные тесты
    public androidTestBuildType = "qa"

    public instrumentalTestAssembleGradleTask = "assembleAndroidTest"
    public instrumentalTestResultPathDirXml = "build/outputs/androidTest-results/instrumental"
    public instrumentalTestResultPathDirHtml = "build/reports/androidTests/instrumental"

    // флаг, показывающий, должно ли имя AVD быть уникальным для текущего job'a
    public generateUniqueAvdNameForJob = true

    // количество попыток перезапуска тестов для одного модуля при падении одного из них
    public instrumentationTestRetryCount = 1
    
    /**
     * Функция, возвращающая имя instrumentation runner для запуска инструментальных тестов.
     *
     * Если для всех модулей проекта используется одинаковый instrumentation runner,
     * то функцию можно переопределить следующим образом:
     *
     * pipeline.getTestInstrumentationRunnerName = { script, prefix -> return "androidx.test.runner.AndroidJUnitRunner" }
     */
    public getTestInstrumentationRunnerName = { script, prefix -> return getDefaultTestInstrumentationRunnerName(script, prefix) }

    public AvdConfig avdConfig = new AvdConfig()

    PrPipelineAndroid(Object script) {
        super(script)
    }

    def init() {
        node = NodeProvider.androidFlutterNode

        preExecuteStageBody = { stage -> preExecuteStageBodyPr(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyPr(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(PRE_MERGE) {
                    preMergeStageBody(script, repoUrl, sourceBranch, destinationBranch, repoCredentialsId)
                },
                stage(BUILD) {
                    AndroidPipelineHelper.buildWithCredentialsStageBodyAndroid(script,
                            buildGradleTask,
                            keystoreCredentials,
                            keystorePropertiesCredentials)
                },
                stage(UNIT_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.unitTestStageBodyAndroid(script,
                            unitTestGradleTask,
                            unitTestResultPathXml,
                            unitTestResultPathDirHtml)
                },
                stage(INSTRUMENTATION_TEST, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.instrumentationTestStageBodyAndroid(
                            script,
                            avdConfig,
                            androidTestBuildType,
                            getTestInstrumentationRunnerName,
                            new AndroidTestConfig(
                                    instrumentalTestAssembleGradleTask,
                                    instrumentalTestResultPathDirXml,
                                    instrumentalTestResultPathDirHtml,
                                    generateUniqueAvdNameForJob,
                                    instrumentationTestRetryCount
                            )
                    )
                },
                stage(STATIC_CODE_ANALYSIS, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    AndroidPipelineHelper.staticCodeAnalysisStageBody(script)
                },

        ]
        finalizeBody = { finalizeStageBody(this) }
    }

    /**
     * Функция, возвращающая имя instrumentation runner, которое будет получено с помощью gradle-таска.
     *
     * Пример такого gradle-таска:
     *
     * task printTestInstrumentationRunnerName {
     *     doLast {
     *         println "$android.defaultConfig.testInstrumentationRunner"
     *     }
     * }
     */
    private getDefaultTestInstrumentationRunnerName = { script, prefix ->
        def defaultInstrumentationRunnerGradleTaskName = "printTestInstrumentationRunnerName"
        return script.sh(
                returnStdout: true,
                script: "./gradlew :$prefix:$defaultInstrumentationRunnerGradleTaskName | tail -4 | head -1"
        )
    }
}

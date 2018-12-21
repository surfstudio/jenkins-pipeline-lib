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
package ru.surfstudio.ci

import ru.surfstudio.ci.utils.android.config.AvdConfig

@Deprecated
class AndroidUtil {

    /**
     * Функция, запускающая существующий или новый эмулятор для выполнения инструментальных тестов
     * @param script контекст вызова
     * @param config конфигурация для эмулятора
     * @param androidTestBuildType build type для запуска инструментальных тестов
     * @param getTestInstrumentationRunnerName функция, возвращающая имя текущего instrumentation runner
     * @param androidTestResultPathXml путь для сохранения xml-отчетов о результатах тестов
     * @param androidTestResultPathDirHtml путь для сохранения html-отчетов о результатах тестов
     */
    static void runInstrumentalTests(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String androidTestResultPathXml,
            String androidTestResultPathDirHtml
    ) {
        ru.surfstudio.ci.utils.AndroidUtil.runInstrumentalTests(
                script,
                config,
                androidTestBuildType,
                getTestInstrumentationRunnerName,
                androidTestResultPathXml,
                androidTestResultPathDirHtml
        )
    }

    /**
     * Функция, которая должна быть вызвана по завершении инструментальных тестов
     */
    static void cleanup(Object script, AvdConfig config) {
        ru.surfstudio.ci.utils.AndroidUtil.cleanup(script, config)
    }

    def static onEmulator(Object script, String avdName, Closure body) {
        ru.surfstudio.ci.utils.AndroidUtil.onEmulator(script, avdName, body)
    }

    /**
     * Run body with extracted Environment Variables:
     *  - storePassword
     *  - keyPassword
     *  - keyAlias
     *  - storeFile
     *
     * @param keystoreCredentials - id credentials with "keystore" file
     * @param keystorePropertiesCredentials - id credentials with "keystore.properties" file, which contains info:
     * storePassword=you_pass
     * keyPassword=you_pass
     * keyAlias=you_alias
     *
     * Example usage:
     * ```
     * AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials){
     *     sh "./gradlew assembleRelease"
     *}* ````
     * How configure gradle to use this variables see here https://bitbucket.org/surfstudio/android-standard/src/snapshot-0.3.0/template/keystore/
     *
     */
    def static withKeystore(Object script, String keystoreCredentials, String keystorePropertiesCredentials, Closure body) {
        ru.surfstudio.ci.utils.AndroidUtil.withKeystore(script, keystoreCredentials, keystorePropertiesCredentials, body)
    }

    static String getGradleVariable(Object script, String file, String varName) {
        return ru.surfstudio.ci.utils.AndroidUtil.getGradleVariable(script, file, varName)
    }

    static String changeGradleVariable(Object script, String file, String varName, String newVarValue) {
        ru.surfstudio.ci.utils.AndroidUtil.changeGradleVariable(script, file, varName, newVarValue)
    }
}

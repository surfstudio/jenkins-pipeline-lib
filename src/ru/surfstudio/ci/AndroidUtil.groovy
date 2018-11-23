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

import ru.surfstudio.ci.pipeline.pr.utils.AndroidTestConfig

class AndroidUtil {

    /**
     * Функция, запускающая существующий или новый эмулятор для выполнения инструментальных тестов
     * @param script контекст вызова
     * @param androidTestConfig конфигурация запуска инструментальных тестов
     * @param finishBody действия, которые должны быть выполнены по завершении инструментальных тестов
     */
    def static runInstrumentalTests(Object script, AndroidTestConfig androidTestConfig, Closure finishBody) {

    }

    def static onEmulator(Object script, String avdName, Closure body) {
        script.timeout(time: 7*60*60, unit: 'SECONDS') { //7 hours
            def ADB = "${script.env.ANDROID_HOME}/platform-tools/adb"
            def EMULATOR = "${script.env.ANDROID_HOME}/tools/emulator"
            script.sh "$ADB devices"
            script.sh "$EMULATOR -list-avds"
            script.lock(avdName) { //блокируем эмулятор
                script.sh "$EMULATOR -avd avd-main -no-window -skin 1440x2560 &"
                script.timeout(time: 120, unit: 'SECONDS') { //2мин ждем запуск девайса
                    script.sh "$ADB wait-for-device"
                }
                //нажимаем кнопку домой
                script.sh "$ADB shell input keyevent 3 &"
                body()
            }
        }
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
     * }
     * ````
     * How configure gradle to use this variables see here https://bitbucket.org/surfstudio/android-standard/src/snapshot-0.3.0/template/keystore/
     *
     */
    def static withKeystore(Object script, String keystoreCredentials, String keystorePropertiesCredentials, Closure body) {
        def bodyStarted = false
        try {
            script.echo "start extract keystoreCredentials: $keystoreCredentials " +
                    "and keystorePropertiesCredentials: $keystorePropertiesCredentials"
            script.withCredentials([
                    script.file(credentialsId: keystoreCredentials, variable: 'KEYSTORE'),
                    script.file(credentialsId: keystorePropertiesCredentials, variable: 'KEYSTORE_PROPERTIES')
            ]) {
                String properties = script.readFile(script.KEYSTORE_PROPERTIES)
                script.echo "extracted keystore properties: \n$properties"
                def vars = properties.tokenize('\n')
                script.withEnv(vars) {
                    script.withEnv(["storeFile=$script.KEYSTORE"]) {
                        bodyStarted = true
                        body()
                    }
                }
            }
        } catch (Exception e){
            if (bodyStarted) {
                throw e
            } else {
                script.echo "^^^^ Ignored exception for read keystore credentials: ${e.toString()} ^^^^"
                body()
            }
        }
    }

    def static String getGradleVariable(Object script, String file, String varName) {
        def String fileBody = script.readFile(file)
        def lines = fileBody.split("\n")
        for (line in lines) {
            def words = line.split(/(;| |\t|=)/).findAll({it?.trim()})
            if(words[0] == varName && words.size() > 1) {
                def value = words[1]
                script.echo "$varName = $value found in file $file"
                return value
            }
        }
        throw script.error("groovy variable with name: $varName not exist in file: $file")
    }

    def static String changeGradleVariable(Object script, String file, String varName, String newVarValue) {
        String oldVarValue = getGradleVariable(script, file, varName)
        String fileBody = script.readFile(file)
        String newFileBody = ""
        def lines = fileBody.split("\n")
        for (line in lines) {
            def words = line.split(/(;| |\t|=)/).findAll({it?.trim()})
            if(words[0] == varName) {
                String updatedLine = line.replace(oldVarValue, newVarValue)
                newFileBody += updatedLine
            } else {
                newFileBody += line
            }
            newFileBody += "\n"
        }
        script.writeFile file: file, text: newFileBody
        script.echo "$varName value changed to $newVarValue in file $file"
    }
}

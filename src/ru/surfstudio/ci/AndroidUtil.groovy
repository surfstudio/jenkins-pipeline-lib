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

import ru.surfstudio.ci.utils.android.AndroidTestConfig
import ru.surfstudio.ci.utils.android.AndroidTestUtil

class AndroidUtil {

    private static String TEMP_GRADLE_OUTPUT_FILENAME = "result"
    private static String NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME = "null"

    private static String SPOON_ZIP_NAME = "spoon.zip"
    private static String SPOON_JAR_NAME = "spoon-runner-1.7.1-jar-with-dependencies.jar"

    /**
     * Функция, запускающая существующий или новый эмулятор для выполнения инструментальных тестов
     * @param script контекст вызова
     * @param config конфигурация запуска инструментальных тестов
     * @param androidTestResultPathXml путь для сохранения отчетов о результатах тестов
     */
    static void runInstrumentalTests(Object script, AndroidTestConfig config, String androidTestResultPathXml) {
        launchEmulator(script, config)
        checkEmulatorStatus(script, config)
        runTests(script, config, androidTestResultPathXml)
    }

    /**
     * Функция, которая должна быть вызвана по завершении инструментальных тестов
     */
    static void cleanup(Object script, AndroidTestConfig config) {
        AndroidTestUtil.closeRunningEmulator(script, config)
        script.sh "rm $TEMP_GRADLE_OUTPUT_FILENAME || true"
    }

    //region Stages of instrumental tests running
    private static void launchEmulator(Object script, AndroidTestConfig config) {
        script.sh "${CommonUtil.getSdkManagerHome(script)} \"${config.sdkId}\""
        def currentTimeoutSeconds = AndroidTestUtil.TIMEOUT_FOR_CREATION_OF_EMULATOR
        def emulatorName = AndroidTestUtil.getEmulatorName(script)

        if (config.reuse) {
            script.echo "try to reuse emulator"
            // проверка, существует ли AVD
            //todo check if AVD params have not changed
            def avdName = AndroidTestUtil.findAvdName(script, config.avdName)
            if (CommonUtil.isNameDefined(avdName)) {
                script.echo "launch reused emulator"
                // проверка, запущен ли эмулятор
                if (CommonUtil.isNameDefined(emulatorName)) {
                    currentTimeoutSeconds = 0
                    script.echo "emulator have been launched already"
                } else {
                    //currentTimeoutSeconds = AndroidTestUtil.SMALL_TIMEOUT_SECONDS
                    currentTimeoutSeconds = 0
                    AndroidTestUtil.launchEmulator(script, config)
                }
            } else { // if AVD is not exists
                AndroidTestUtil.createAndLaunchNewEmulator(script, config)
            }
        } else { // if not reuse
            closeAndCreateEmulator(script, config, "not reuse")
        }

        sleep(script, currentTimeoutSeconds)
    }

    private static void checkEmulatorStatus(Object script, AndroidTestConfig config) {
        def emulatorName = AndroidTestUtil.getEmulatorName(script)
        if (AndroidTestUtil.isEmulatorOffline(script) || !CommonUtil.isNameDefined(emulatorName)) {
            closeAndCreateEmulator(script, config, "emulator is offline")
            sleep(script, AndroidTestUtil.TIMEOUT_FOR_CREATION_OF_EMULATOR)
        } else {
            script.echo "emulator is online"
        }
    }

    private static void runTests(Object script, AndroidTestConfig config, String androidTestResultPathXml) {
        script.echo "start running tests"
        def emulatorName = AndroidTestUtil.getEmulatorName(script)
        
        def spoonJarFile = script.libraryResource resource: SPOON_JAR_NAME, encoding: "Base64"
        script.writeFile file: SPOON_JAR_NAME, text: spoonJarFile, encoding: "Base64"

        //def spoonZipFile = script.libraryResource SPOON_ZIP_NAME
        //script.writeFile file: SPOON_ZIP_NAME, text: spoonZipFile

        //script.sh "ls -la"
        //script.sh "unzip $SPOON_ZIP_NAME -d spoon"
        //script.sh "cd spoon; ls -la"

        script.sh "unzip $SPOON_JAR_NAME -d spoon"
        script.sh "cd spoon; ls -la"
        
        script.sh "java -jar spoon/$SPOON_JAR_NAME"

        /*
        AndroidTestUtil.getApkList(script, AndroidTestUtil.ANDROID_TEST_APK_SUFFIX).each {
            def currentApkName = "$it"
            def apkMainFolder = AndroidTestUtil.getApkFolderName(script, currentApkName).trim()

            // Проверка, содержит ли проект модули
            def apkModuleName = AndroidTestUtil.getApkModuleName(script, currentApkName).trim()
            def apkPrefix = (apkModuleName != "build") ? apkModuleName : apkMainFolder
            def testReportFileNameSuffix = apkMainFolder

            // Получение имени testInstrumentationRunner для запусков тестов текущего модуля
            def currentInstrumentationGradleTaskRunnerName

            if (apkMainFolder != apkPrefix) {
                currentInstrumentationGradleTaskRunnerName = AndroidTestUtil.getInstrumentationGradleTaskRunnerName(
                        "$apkMainFolder:$apkPrefix",
                        config
                )
                testReportFileNameSuffix += "-$apkPrefix"
            } else {
                currentInstrumentationGradleTaskRunnerName = AndroidTestUtil.getInstrumentationGradleTaskRunnerName(
                        "$apkMainFolder",
                        config
                )
            }
            script.echo "test report dirs $testReportFileNameSuffix $currentInstrumentationGradleTaskRunnerName"

            // Находим APK для testBuildType, заданного в конфиге, и имя тестового пакета
            def testBuildTypeApkList = AndroidTestUtil.getApkList(script, config.testBuildType, apkMainFolder)

            // Проверка, существует ли APK с заданным testBuildType
            if (testBuildTypeApkList.size() > 0) {
                def testBuildTypeApkName = testBuildTypeApkList[0]
                if (CommonUtil.isNameDefined(testBuildTypeApkName)) {
                    script.sh "./gradlew '${CommonUtil.formatString(currentInstrumentationGradleTaskRunnerName)}' \
                    > $TEMP_GRADLE_OUTPUT_FILENAME"

                    def currentInstrumentationRunnerName = CommonUtil.formatString(
                            AndroidTestUtil.getInstrumentationRunnerName(
                                    script,
                                    TEMP_GRADLE_OUTPUT_FILENAME
                            )
                    )

                    script.echo "currentInstrumentationRunnerName $currentInstrumentationRunnerName"

                    // Проверка, определен ли testInstrumentationRunner для текущего модуля
                    if (currentInstrumentationRunnerName != NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME) {

                        // Для переиспользуемого эмулятора необходимо удалить предыдущую версию APK для текущего модуля
                        def testBuildTypePackageName = AndroidTestUtil.getPackageNameFromApk(script, testBuildTypeApkName)
                        if (config.reuse) {
                            AndroidTestUtil.uninstallApk(script, emulatorName, testBuildTypePackageName)
                        }

                        def projectRootDir = "${CommonUtil.getShCommandOutput(script, "pwd")}/"
                        def spoonOutputDir = "${CommonUtil.formatString(projectRootDir, testReportFileNameSuffix)}/build/outputs/spoon-output"
                        script.sh "mkdir -p $spoonOutputDir"

                        script.sh "java -jar $SPOON_JAR_NAME \
                            --apk \"${CommonUtil.formatString(projectRootDir, testBuildTypeApkName)}\" \
                            --test-apk \"${CommonUtil.formatString(projectRootDir, currentApkName)}\" \
                            --output \"${CommonUtil.formatString(spoonOutputDir)}\" \
                            -serial \"${CommonUtil.formatString(emulatorName)}\""

                        script.sh "cp $spoonOutputDir/junit-reports/*.xml $androidTestResultPathXml/report-${apkMainFolder}.xml"
                    }
                }
            }
        }*/
    }
    //endregion

    //region Helpful functions
    private static void closeAndCreateEmulator(Object script, AndroidTestConfig config, String message) {
        script.echo message
        AndroidTestUtil.closeRunningEmulator(script, config)
        AndroidTestUtil.createAndLaunchNewEmulator(script, config)
    }

    private static void sleep(Object script, Integer timeout) {
        if (timeout > 0) {
            script.echo "waiting $timeout seconds..."
            script.sh "sleep $timeout"
        }
    }
    //endregion

    def static onEmulator(Object script, String avdName, Closure body) {
        script.timeout(time: 7 * 60 * 60, unit: 'SECONDS') { //7 hours
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
     *}* ````
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
        } catch (Exception e) {
            if (bodyStarted) {
                throw e
            } else {
                script.echo "^^^^ Ignored exception for read keystore credentials: ${e.toString()} ^^^^"
                body()
            }
        }
    }

    static String getGradleVariable(Object script, String file, String varName) {
        String fileBody = script.readFile(file)
        def lines = fileBody.split("\n")
        for (line in lines) {
            def words = line.split(/(;| |\t|=)/).findAll({ it?.trim() })
            if (words[0] == varName && words.size() > 1) {
                def value = words[1]
                script.echo "$varName = $value found in file $file"
                return value
            }
        }
        throw script.error("groovy variable with name: $varName not exist in file: $file")
    }

    static String changeGradleVariable(Object script, String file, String varName, String newVarValue) {
        String oldVarValue = getGradleVariable(script, file, varName)
        String fileBody = script.readFile(file)
        String newFileBody = ""
        def lines = fileBody.split("\n")
        for (line in lines) {
            def words = line.split(/(;| |\t|=)/).findAll({ it?.trim() })
            if (words[0] == varName) {
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

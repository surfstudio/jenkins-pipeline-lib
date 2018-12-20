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

    // имя временного файла с результатами выполнения gradle-таска
    private static String TEMP_GRADLE_OUTPUT_FILENAME = "result"
    private static String NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME = "null"

    private static String SPOON_JAR_NAME = "spoon-runner-1.7.1-jar-with-dependencies.jar"
    private static Integer TIMEOUT_PER_TEST = 60 * 2 // seconds

    private static Boolean reusedEmulator = false

    /**
     * Функция, запускающая существующий или новый эмулятор для выполнения инструментальных тестов
     * @param script контекст вызова
     * @param config конфигурация запуска инструментальных тестов
     * @param instrumentalTestGradleTaskOutputPathDir путь для временного файла с результатом выполения gradle-таска
     * @param androidTestResultPathXml путь для сохранения отчетов о результатах тестов
     */
    static void runInstrumentalTests(
            Object script,
            AndroidTestConfig config,
            String instrumentalTestGradleTaskOutputPathDir,
            String androidTestResultPathXml
    ) {
        launchEmulator(script, config)
        checkEmulatorStatus(script, config)
        runTests(script, config, instrumentalTestGradleTaskOutputPathDir, androidTestResultPathXml)
    }

    /**
     * Функция, которая должна быть вызвана по завершении инструментальных тестов
     */
    static void cleanup(Object script, AndroidTestConfig config) {
        AndroidTestUtil.closeRunningEmulator(script, config)
    }

    //region Stages of instrumental tests running
    private static void launchEmulator(Object script, AndroidTestConfig config) {
        script.sh "${CommonUtil.getSdkManagerHome(script)} \"${config.sdkId}\""
        def currentTimeoutSeconds = AndroidTestUtil.EMULATOR_TIMEOUT
        def emulatorName = AndroidTestUtil.getEmulatorName(script)

        reusedEmulator = config.reuse
        if (reusedEmulator) {
            script.echo "try to reuse emulator"
            script.sh "${CommonUtil.getAvdManagerHome(script)} list avd"
            // проверка, существует ли AVD
            def avdName = AndroidTestUtil.findAvdName(script, config.avdName)
            if (CommonUtil.isNameDefined(avdName)) {
                script.echo "launch reused emulator"
                // проверка, запущен ли эмулятор
                if (CommonUtil.isNameDefined(emulatorName)) {
                    script.echo "emulator have been launched already"
                    currentTimeoutSeconds = 0
                } else {
                    AndroidTestUtil.launchEmulator(script, config)
                }
            } else { // if AVD is not exists
                reusedEmulator = false
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
            sleep(script, AndroidTestUtil.EMULATOR_TIMEOUT)
        } else {
            script.echo "emulator is online"
        }
    }

    private static void runTests(
            Object script,
            AndroidTestConfig config,
            String instrumentalTestGradleTaskOutputPathDir,
            String androidTestResultPathXml
    ) {
        script.echo "start running tests"
        def emulatorName = AndroidTestUtil.getEmulatorName(script)

        script.sh "${CommonUtil.getAdbHome(script)} devices"

        def spoonJarFile = script.libraryResource resource: SPOON_JAR_NAME, encoding: "Base64"
        script.writeFile file: SPOON_JAR_NAME, text: spoonJarFile, encoding: "Base64"

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

            def gradleOutputFileName = "$instrumentalTestGradleTaskOutputPathDir/$TEMP_GRADLE_OUTPUT_FILENAME"

            // Проверка, существует ли APK с заданным testBuildType
            if (testBuildTypeApkList.size() > 0) {
                def testBuildTypeApkName = testBuildTypeApkList[0]
                if (CommonUtil.isNameDefined(testBuildTypeApkName)) {
                    script.sh "./gradlew '${CommonUtil.formatString(currentInstrumentationGradleTaskRunnerName)}' \
                    > $gradleOutputFileName"

                    def currentInstrumentationRunnerName = CommonUtil.formatString(
                            AndroidTestUtil.getInstrumentationRunnerName(
                                    script,
                                    gradleOutputFileName
                            )
                    )

                    script.echo "currentInstrumentationRunnerName $currentInstrumentationRunnerName"

                    // Проверка, определен ли testInstrumentationRunner для текущего модуля
                    if (currentInstrumentationRunnerName != NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME) {
                        def projectRootDir = "${CommonUtil.getShCommandOutput(script, "pwd")}/"
                        def spoonOutputDir = "${CommonUtil.formatString(projectRootDir, testReportFileNameSuffix)}/build/outputs/spoon-output"
                        CommonUtil.mkdir(script, spoonOutputDir)

                        script.sh "java -jar $SPOON_JAR_NAME \
                            --apk \"${CommonUtil.formatString(projectRootDir, testBuildTypeApkName)}\" \
                            --test-apk \"${CommonUtil.formatString(projectRootDir, currentApkName)}\" \
                            --output \"${CommonUtil.formatString(spoonOutputDir)}\" \
                            --adb-timeout $TIMEOUT_PER_TEST \
                            -serial \"${CommonUtil.formatString(emulatorName)}\""

                        script.sh "cp $spoonOutputDir/junit-reports/*.xml $androidTestResultPathXml/report-${apkMainFolder}.xml"

                        // Для переиспользуемого эмулятора необходимо удалить предыдущую версию APK для текущего модуля
                        if (reusedEmulator) {
                            def testBuildTypePackageName = AndroidTestUtil.getPackageNameFromApk(script, testBuildTypeApkName)
                            AndroidTestUtil.uninstallApk(script, emulatorName, testBuildTypePackageName)
                        }
                    }
                }
            }
        }
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

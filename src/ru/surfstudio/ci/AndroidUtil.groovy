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

import ru.surfstudio.ci.utils.android.AvdConfig
import ru.surfstudio.ci.utils.android.AndroidTestUtil

class AndroidUtil {

    // имя временного файла с результатами выполнения gradle-таска
    private static String NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME = "null"

    private static String SPOON_JAR_NAME = "spoon-runner-1.7.1-jar-with-dependencies.jar"
    private static String BASE64_ENCODING = "Base64"
    private static Integer TIMEOUT_PER_TEST = 60 * 2 // seconds

    private static Boolean reusedEmulator = false

    /**
     * Версия build tools для получения корректного пути к актуальной утилите aapt.
     *
     * todo Обновить эту константу при обновлении build tools
     */
    private static String BUILD_TOOLS_VERSION = "28.0.3"

    /**
     * Функция, запускающая существующий или новый эмулятор для выполнения инструментальных тестов
     * @param script контекст вызова
     * @param config конфигурация запуска инструментальных тестов
     * @param androidTestBuildType build type для запуска инструментальных тестов
     * @param getTestInstrumentationRunnerName функция, возвращающая имя текущего instrumentation runner
     * @param instrumentalTestGradleTaskOutputPathDir путь для временного файла с результатом выполения gradle-таска
     * @param androidTestResultPathXml путь для сохранения отчетов о результатах тестов
     */
    static void runInstrumentalTests(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String instrumentalTestGradleTaskOutputPathDir,
            String androidTestResultPathXml
    ) {
        launchEmulator(script, config)
        checkEmulatorStatus(script, config)
        runTests(
                script,
                config,
                androidTestBuildType,
                getTestInstrumentationRunnerName,
                instrumentalTestGradleTaskOutputPathDir,
                androidTestResultPathXml
        )
    }

    /**
     * Функция, которая должна быть вызвана по завершении инструментальных тестов
     */
    static void cleanup(Object script, AvdConfig config) {
        AndroidTestUtil.closeRunningEmulator(script, config)
    }

    //region Stages of instrumental tests running
    private static void launchEmulator(Object script, AvdConfig config) {
        script.sh "${CommonUtil.getSdkManagerHome(script)} \"${config.sdkId}\""
        def currentTimeoutSeconds = AndroidTestUtil.EMULATOR_TIMEOUT
        def emulatorName = AndroidTestUtil.getEmulatorName(script)

        reusedEmulator = config.reuse
        if (reusedEmulator) {
            script.echo "try to reuse emulator"
            script.sh "${CommonUtil.getAvdManagerHome(script)} list avd"
            // проверка, существует ли AVD
            def avdName = AndroidTestUtil.isAvdExists(script, config.avdName)
            if (CommonUtil.isNotNullOrEmpty(avdName)) {
                script.echo "launch reused emulator"
                // проверка, запущен ли эмулятор
                if (CommonUtil.isNotNullOrEmpty(emulatorName)) {
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

    private static void checkEmulatorStatus(Object script, AvdConfig config) {
        def emulatorName = AndroidTestUtil.getEmulatorName(script)
        if (AndroidTestUtil.isEmulatorOffline(script) || !CommonUtil.isNotNullOrEmpty(emulatorName)) {
            closeAndCreateEmulator(script, config, "emulator is offline")
            sleep(script, AndroidTestUtil.EMULATOR_TIMEOUT)
        } else {
            script.echo "emulator is online"
        }
    }

    private static void runTests(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String instrumentalTestGradleTaskOutputPathDir,
            String androidTestResultPathXml
    ) {
        script.echo "start running tests"
        def emulatorName = AndroidTestUtil.getEmulatorName(script)

        script.sh "${CommonUtil.getAdbHome(script)} devices"

        def spoonJarFile = script.libraryResource resource: SPOON_JAR_NAME, encoding: BASE64_ENCODING
        script.writeFile file: SPOON_JAR_NAME, text: spoonJarFile, encoding: BASE64_ENCODING

        AndroidTestUtil.getApkList(script, AndroidTestUtil.ANDROID_TEST_APK_SUFFIX).each {
            def currentApkName = "$it"
            def apkMainFolder = AndroidTestUtil.getApkFolderName(script, currentApkName).trim()

            // Проверка, содержит ли проект модули
            def apkModuleName = AndroidTestUtil.getApkModuleName(script, currentApkName).trim()
            def apkPrefix = (apkModuleName != "build") ? apkModuleName : apkMainFolder
            def testReportFileNameSuffix = apkMainFolder

            // Получение префикса модуля для запуска gradle-таска
            def gradleTaskPrefix = apkMainFolder

            if (apkMainFolder != apkPrefix) {
                gradleTaskPrefix = "$apkMainFolder:$apkPrefix"
                testReportFileNameSuffix += "-$apkPrefix"
            }

            // Находим APK для androidTestBuildType, заданного в конфиге, и имя тестового пакета
            def testBuildTypeApkList = AndroidTestUtil.getApkList(script, androidTestBuildType, apkMainFolder)

            //def gradleOutputFileName = "$instrumentalTestGradleTaskOutputPathDir/$TEMP_GRADLE_OUTPUT_FILENAME"

            // Проверка, существует ли APK с заданным androidTestBuildType
            if (testBuildTypeApkList.size() > 0) {
                def testBuildTypeApkName = testBuildTypeApkList[0]
                if (CommonUtil.isNotNullOrEmpty(testBuildTypeApkName)) {
                    def currentInstrumentationRunnerName = getTestInstrumentationRunnerName(script, gradleTaskPrefix)
                    script.echo "currentInstrumentationRunnerName $currentInstrumentationRunnerName"
                    return

                    // Проверка, определен ли testInstrumentationRunner для текущего модуля
                    if (currentInstrumentationRunnerName != NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME) {
                        String projectRootDir = "${script.sh(returnStdout: true, script: "pwd")}/"
                        String spoonOutputDir = "${formatArgsForShellCommand(projectRootDir, testReportFileNameSuffix)}/build/outputs/spoon-output"
                        script.sh "mkdir -p $spoonOutputDir"

                        script.sh "java -jar $SPOON_JAR_NAME \
                            --apk \"${formatArgsForShellCommand(projectRootDir, testBuildTypeApkName)}\" \
                            --test-apk \"${formatArgsForShellCommand(projectRootDir, currentApkName)}\" \
                            --output \"${formatArgsForShellCommand(spoonOutputDir)}\" \
                            --adb-timeout $TIMEOUT_PER_TEST \
                            -serial \"${formatArgsForShellCommand(emulatorName)}\""

                        script.sh "cat $spoonOutputDir/logs/*/*/*.html"
                        script.sh "cp $spoonOutputDir/junit-reports/*.xml $androidTestResultPathXml/report-${apkMainFolder}.xml"

                        // Для переиспользуемого эмулятора необходимо удалить предыдущую версию APK для текущего модуля
                        if (reusedEmulator) {
                            def testBuildTypePackageName = AndroidTestUtil.getPackageNameFromApk(
                                    script,
                                    testBuildTypeApkName,
                                    BUILD_TOOLS_VERSION)
                            AndroidTestUtil.uninstallApk(script, emulatorName, testBuildTypePackageName)
                        }
                    }
                }
            }
        }
    }
    //endregion

    //region Helpful functions
    private static void closeAndCreateEmulator(Object script, AvdConfig config, String message) {
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

    /**
     * Функция, форматирующая аргументы и конкатенирующая их.
     * Возвращает строку, которую можно безопасно подставить в shell-команду
     */
    private static String formatArgsForShellCommand(String... args) {
        String result = ""
        args.each {
            result += it.replaceAll('\n', '')
        }
        return result
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

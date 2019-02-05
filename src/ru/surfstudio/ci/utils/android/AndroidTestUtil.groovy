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
package ru.surfstudio.ci.utils.android

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.utils.android.config.AvdConfig

/**
 * Утилиты для инструментальных тестов Android
 */
class AndroidTestUtil {

    static String ANDROID_TEST_APK_SUFFIX = "androidTest"

    private static String SPOON_JAR_NAME = "spoon-runner-1.7.1-jar-with-dependencies.jar"
    private static Integer TIMEOUT_PER_TEST = 60 * 5 // seconds

    //region helpful constants
    private static String NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME = "null"
    private static String BASE64_ENCODING = "Base64"

    private static String TEST_COUNT_STRING = "testCount="
    private static String FAILURE_STRING = "failure"

    private static String ZERO_STRING = "0"
    private static String DIVIDER = "="

    private static Integer SUCCESS_CODE = 0
    private static Integer ERROR_CODE = 1
    //endregion

    //region messages
    private static String RUN_TESTS_MESSAGE = "RUN TESTS FOR:"
    private static String NO_INSTRUMENTAL_TESTS_MESSAGE = "NO INSTRUMENTAL TESTS IN MODULE:"
    private static String REPEAT_TESTS_MESSAGE = "REPEAT TESTS FOR:"
    private static String TEST_RESULT_CODE_MESSAGE = "TEST RESULT CODE:"
    //endregion

    /**
     * Версия build tools для получения корректного пути к актуальной утилите aapt.
     *
     * todo Обновить эту константу при обновлении build tools
     */
    private static String BUILD_TOOLS_VERSION = "28.0.3"

    /**
     * Функция, запускающая существующий или новый эмулятор для выполнения инструментальных тестов
     * @param script контекст вызова
     * @param config конфигурация для эмулятора
     * @param androidTestBuildType build type для запуска инструментальных тестов
     * @param getTestInstrumentationRunnerName функция, возвращающая имя текущего instrumentation runner
     * @param androidTestResultPathXml путь для сохранения xml-отчетов о результатах тестов
     * @param androidTestResultPathDirHtml путь для сохранения html-отчетов о результатах тестов
     * @param generateUniqueAvdNameForJob флаг, показывающий, должно ли имя AVD быть уникальным для текущего job'a
     * @param instrumentationTestRetryCount количество попыток перезапуска тестов для одного модуля при падении одного из них
     */
    static void runInstrumentalTests(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String androidTestResultPathXml,
            String androidTestResultPathDirHtml,
            Boolean generateUniqueAvdNameForJob,
            Integer instrumentationTestRetryCount
    ) {
        if (generateUniqueAvdNameForJob) {
            config.avdName = "avd-${script.env.JOB_NAME}"
            script.echo "avdName = ${config.avdName}"
        }
        script.echo "waiting for avd ${config.avdName}"
        script.lock(config.avdName) {
            launchEmulator(script, config)
            checkEmulatorStatus(script, config)
            runTests(
                    script,
                    config,
                    androidTestBuildType,
                    getTestInstrumentationRunnerName,
                    androidTestResultPathXml,
                    androidTestResultPathDirHtml,
                    instrumentationTestRetryCount
            )
        }
    }

    /**
     * Функция, которая должна быть вызвана по завершении инструментальных тестов
     */
    static void cleanup(Object script, AvdConfig config) {
        EmulatorUtil.closeRunningEmulator(script, config)
    }

    //region Stages of instrumental tests running
    private static void launchEmulator(Object script, AvdConfig config) {
        script.sh "${CommonUtil.getSdkManagerHome(script)} \"${config.sdkId}\""
        EmulatorUtil.createAndLaunchNewEmulator(script, config)
    }

    private static void checkEmulatorStatus(Object script, AvdConfig config) {
        if (EmulatorUtil.isEmulatorOffline(script, config.emulatorName) || !CommonUtil.isNotNullOrEmpty(config.emulatorName)) {
            EmulatorUtil.closeAndCreateEmulator(script, config, "emulator is offline")
        } else {
            script.echo "emulator is online"
        }
    }

    private static void runTests(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String androidTestResultPathXml,
            String androidTestResultPathDirHtml,
            Integer instrumentationTestRetryCount
    ) {
        script.echo "start running tests"

        script.sh "${CommonUtil.getAdbHome(script)} devices"

        AdbUtil.disableAnimations(script, config.emulatorName)
        script.sh "${AdbUtil.getAdbShellCommand(script, config.emulatorName)} input keyevent 82 &"

        def spoonJarFile = script.libraryResource resource: SPOON_JAR_NAME, encoding: BASE64_ENCODING
        script.writeFile file: SPOON_JAR_NAME, text: spoonJarFile, encoding: BASE64_ENCODING

        boolean allTestsPassed = true

        ApkUtil.getApkList(script, "$androidTestBuildType-$ANDROID_TEST_APK_SUFFIX*").each {
            def currentApkName = "$it"
            def apkMainFolder = ApkUtil.getApkFolderName(script, currentApkName).trim()

            // Проверка, содержит ли проект модули
            def apkModuleName = ApkUtil.getApkModuleName(script, currentApkName).trim()
            def apkPrefix = (apkModuleName != "build") ? apkModuleName : apkMainFolder
            def testReportFileNameSuffix = apkMainFolder

            // Фактическое имя модуля, в котором запущены тесты (отличается от apkMainFolder, если проект содержит вложенные модули)
            def testModuleName = apkMainFolder

            // Получение префикса модуля для запуска gradle-таска
            def gradleTaskPrefix = apkMainFolder

            if (apkMainFolder != apkPrefix) {
                gradleTaskPrefix = "$apkMainFolder:$apkPrefix"
                testReportFileNameSuffix += "/$testReportFileNameSuffix-$apkPrefix"
                testModuleName += "/$apkPrefix"
            }

            // Находим APK для androidTestBuildType, заданного в конфиге
            def testBuildTypeApkList = ApkUtil.getApkList(
                    script,
                    "$androidTestBuildType*",
                    ANDROID_TEST_APK_SUFFIX,
                    apkMainFolder
            )

            // Проверка, существует ли APK с заданным androidTestBuildType
            if (testBuildTypeApkList.size() > 0) {
                def testBuildTypeApkName = testBuildTypeApkList[0]
                if (CommonUtil.isNotNullOrEmpty(testBuildTypeApkName)) {
                    def currentInstrumentationRunnerName = getTestInstrumentationRunnerName(script, gradleTaskPrefix).trim()

                    // Проверка, определен ли testInstrumentationRunner для текущего модуля.
                    // Имя testInstrumentationRunner должно состоять из одного слова.
                    if (currentInstrumentationRunnerName.split().length == 1 &&
                            currentInstrumentationRunnerName != CommonUtil.EMPTY_STRING &&
                            currentInstrumentationRunnerName != NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME) {

                        script.echo "currentInstrumentationRunnerName $currentInstrumentationRunnerName"

                        String projectRootDir = "${script.sh(returnStdout: true, script: "pwd")}/"
                        String spoonOutputDir = "${formatArgsForShellCommand(projectRootDir, testReportFileNameSuffix)}/build/outputs/spoon-output"
                        script.sh "mkdir -p $spoonOutputDir"

                        deleteApk(script, testBuildTypeApkName, config.emulatorName)
                        printMessage(script, "$RUN_TESTS_MESSAGE $testModuleName")

                        int countOfLaunch = 0, testResultCode = 0
                        while (countOfLaunch <= instrumentationTestRetryCount) {
                            if (countOfLaunch > 0) {
                                printMessage(script, "$REPEAT_TESTS_MESSAGE $testModuleName")
                            }

                            def testResultLogs = script.sh(
                                    returnStdout: true,
                                    script: "java -jar $SPOON_JAR_NAME \
                                    --apk \"${formatArgsForShellCommand(projectRootDir, testBuildTypeApkName)}\" \
                                    --test-apk \"${formatArgsForShellCommand(projectRootDir, currentApkName)}\" \
                                    --output \"${formatArgsForShellCommand(spoonOutputDir)}\" \
                                    --adb-timeout $TIMEOUT_PER_TEST \
                                    --debug --grant-all --no-animations \
                                    -serial \"${formatArgsForShellCommand(config.emulatorName)}\""
                            )
                            script.echo testResultLogs
                            testResultLogs = testResultLogs.split()

                            def testCountString = testResultLogs.find { it.contains(TEST_COUNT_STRING) }
                            def testCount = testCountString.split().find { it.contains(TEST_COUNT_STRING) }.split(DIVIDER).last()

                            if (testCount == ZERO_STRING) {
                                printMessage(script, "$NO_INSTRUMENTAL_TESTS_MESSAGE $testModuleName")
                                break
                            }

                            def testFailureString = testResultLogs.find { it.contains(FAILURE_STRING) }
                            testResultCode = testFailureString == null && testCountString != null ? SUCCESS_CODE : ERROR_CODE
                            printMessage(script, "$TEST_RESULT_CODE_MESSAGE $testResultCode")

                            if (testResultCode == SUCCESS_CODE) {
                                break
                            }

                            countOfLaunch++
                            deleteApk(script, testBuildTypeApkName, config.emulatorName)
                        }

                        allTestsPassed = allTestsPassed && (testResultCode == SUCCESS_CODE)

                        script.sh "cp $spoonOutputDir/junit-reports/*.xml $androidTestResultPathXml/report-${apkMainFolder}.xml"
                        script.sh "cp -r $spoonOutputDir $androidTestResultPathDirHtml/${apkMainFolder}"
                    }
                } // if (CommonUtil.isNotNullOrEmpty(testBuildTypeApkName)) ...
            } // if (testBuildTypeApkList.size() > 0)...
        } // ApkUtil.getApkList...

        if (!allTestsPassed) {
            throw new Exception("Instrumentation test failed")
        }
    }
    //endregion

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

    /**
     * Функция для удаления данного APK с эмулятора
     */
    private static void deleteApk(Object script, String apkName, String emulatorName) {
        try {
            def testBuildTypePackageName = ApkUtil.getPackageNameFromApk(
                    script,
                    apkName,
                    BUILD_TOOLS_VERSION)
            ApkUtil.uninstallApk(script, emulatorName, testBuildTypePackageName)
        } catch (ignored) {
            script.echo "error while unistalling apk $apkName"
        }
    }

    private static void printMessage(Object script, String message) {
        script.echo "---------------------------------- $message ----------------------------------"
    }

    /**
     * Функция для запуска инструментальных тестов
     * @param script контекст вызова
     * @param emulatorName имя эмулятора, на котором будут запущены тесты
     * @param testPackageWithRunner test.package.name/AndroidInstrumentalRunnerName для запуска тестов
     */
    static void runInstrumentalTests(Object script, String emulatorName, String testPackageWithRunner) {
        script.sh "${AdbUtil.getAdbShellCommand(script, emulatorName)} \
            am instrument -w -r -e debug false ${formatArgsForShellCommand(testPackageWithRunner)}"
    }
}

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

    private static String NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME = "null"

    private static String SPOON_JAR_NAME = "spoon-runner-1.7.1-jar-with-dependencies.jar"
    private static String BASE64_ENCODING = "Base64"
    private static Integer TIMEOUT_PER_TEST = 60 * 3 // seconds

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
     */
    static void runInstrumentalTests(
            Object script,
            AvdConfig config,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String androidTestResultPathXml,
            String androidTestResultPathDirHtml
    ) {
        launchEmulator(script, config)
        checkEmulatorStatus(script, config)
        runTests(
                script,
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
        EmulatorUtil.closeRunningEmulator(script, config)
    }

    //region Stages of instrumental tests running
    private static void launchEmulator(Object script, AvdConfig config) {
        script.sh "${CommonUtil.getSdkManagerHome(script)} \"${config.sdkId}\""
        def currentTimeoutSeconds = EmulatorUtil.EMULATOR_TIMEOUT
        def emulatorName = EmulatorUtil.getEmulatorName(script)

        if (config.reuse) {
            script.echo "try to reuse emulator"
            script.sh "${CommonUtil.getAvdManagerHome(script)} list avd"
            // проверка, существует ли AVD
            def avdName = AvdUtil.isAvdExists(script, config.avdName)
            if (CommonUtil.isNotNullOrEmpty(avdName)) {
                script.echo "launch reused emulator"
                // проверка, запущен ли эмулятор
                if (CommonUtil.isNotNullOrEmpty(emulatorName)) {
                    script.echo "emulator have been launched already"
                    currentTimeoutSeconds = 0
                } else {
                    EmulatorUtil.launchEmulator(script, config)
                }
            } else { // if AVD is not exists
                EmulatorUtil.createAndLaunchNewEmulator(script, config)
            }
        } else { // if not reuse
            EmulatorUtil.closeAndCreateEmulator(script, config, "not reuse")
        }

        sleep(script, currentTimeoutSeconds)
    }

    private static void checkEmulatorStatus(Object script, AvdConfig config) {
        def emulatorName = EmulatorUtil.getEmulatorName(script)
        if (EmulatorUtil.isEmulatorOffline(script) || !CommonUtil.isNotNullOrEmpty(emulatorName)) {
            EmulatorUtil.closeAndCreateEmulator(script, config, "emulator is offline")
            sleep(script, EmulatorUtil.EMULATOR_TIMEOUT)
        } else {
            script.echo "emulator is online"
            script.sh "${AdbUtil.getAdbShellCommand(script, emulatorName)} input keyevent 82 &"
        }
    }

    private static void runTests(
            Object script,
            String androidTestBuildType,
            Closure getTestInstrumentationRunnerName,
            String androidTestResultPathXml,
            String androidTestResultPathDirHtml
    ) {
        script.echo "start running tests"
        def emulatorName = EmulatorUtil.getEmulatorName(script)

        script.sh "${CommonUtil.getAdbHome(script)} devices"

        def spoonJarFile = script.libraryResource resource: SPOON_JAR_NAME, encoding: BASE64_ENCODING
        script.writeFile file: SPOON_JAR_NAME, text: spoonJarFile, encoding: BASE64_ENCODING

        boolean allTestsPasses = true

        ApkUtil.getApkList(script, "$androidTestBuildType-$ANDROID_TEST_APK_SUFFIX*").each {
            def currentApkName = "$it"
            def apkMainFolder = ApkUtil.getApkFolderName(script, currentApkName).trim()

            // Проверка, содержит ли проект модули
            def apkModuleName = ApkUtil.getApkModuleName(script, currentApkName).trim()
            def apkPrefix = (apkModuleName != "build") ? apkModuleName : apkMainFolder
            def testReportFileNameSuffix = apkMainFolder

            // Получение префикса модуля для запуска gradle-таска
            def gradleTaskPrefix = apkMainFolder

            if (apkMainFolder != apkPrefix) {
                gradleTaskPrefix = "$apkMainFolder:$apkPrefix"
                testReportFileNameSuffix += "-$apkPrefix"
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
                    script.echo "currentInstrumentationRunnerName $currentInstrumentationRunnerName"

                    // Проверка, определен ли testInstrumentationRunner для текущего модуля
                    if (currentInstrumentationRunnerName != CommonUtil.EMPTY_STRING &&
                            currentInstrumentationRunnerName != NOT_DEFINED_INSTRUMENTATION_RUNNER_NAME) {
                        String projectRootDir = "${script.sh(returnStdout: true, script: "pwd")}/"
                        String spoonOutputDir = "${formatArgsForShellCommand(projectRootDir, testReportFileNameSuffix)}/build/outputs/spoon-output"
                        script.sh "mkdir -p $spoonOutputDir"

                        // Для переиспользуемого эмулятора необходимо удалить предыдущую версию APK для текущего модуля
                        try {
                            def testBuildTypePackageName = ApkUtil.getPackageNameFromApk(
                                    script,
                                    testBuildTypeApkName,
                                    BUILD_TOOLS_VERSION)
                            ApkUtil.uninstallApk(script, emulatorName, testBuildTypePackageName)
                        } catch (ignored) {
                            script.echo "error while unistalling apk $testBuildTypeApkName"
                        }

                        script.echo "run tests for $apkMainFolder"
                        def testResultCode = script.sh(
                                returnStatus: true,
                                script: "java -jar $SPOON_JAR_NAME \
                                    --apk \"${formatArgsForShellCommand(projectRootDir, testBuildTypeApkName)}\" \
                                    --test-apk \"${formatArgsForShellCommand(projectRootDir, currentApkName)}\" \
                                    --output \"${formatArgsForShellCommand(spoonOutputDir)}\" \
                                    --adb-timeout $TIMEOUT_PER_TEST \
                                    --debug --fail-on-failure --grant-all \
                                    -serial \"${formatArgsForShellCommand(emulatorName)}\""
                        )
                        allTestsPasses = allTestsPasses && (testResultCode == 0)

                        script.sh "cp $spoonOutputDir/junit-reports/*.xml $androidTestResultPathXml/report-${apkMainFolder}.xml"
                        script.sh "cp -r $spoonOutputDir $androidTestResultPathDirHtml/${apkMainFolder}"
                    }
                } // if (CommonUtil.isNotNullOrEmpty(testBuildTypeApkName)) ...
            } // if (testBuildTypeApkList.size() > 0)...
        } // ApkUtil.getApkList...

        if (!allTestsPasses) {
            throw new Exception("Instrumentation test failed")
        }
    }
    //endregion

    //region Helpful functions
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

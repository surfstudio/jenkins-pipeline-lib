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

/**
 * Утилиты для инструментальных тестов Android
 */
class AndroidTestUtil {

    static String ANDROID_TEST_APK_SUFFIX = "androidTest"
    private static String ANDROID_MANIFEST_FILE_NAME = "AndroidManifest.xml"

    // значение таймаута для создания и загрузки нового эмулятора
    static Integer EMULATOR_TIMEOUT = 5

    //region Emulator utils
    /**
     * Функция, возвращающая имя запущенного эмулятора
     */
    static String getEmulatorName(Object script) {
        return getEmulatorInfo(script, 1).trim()
    }

    /**
     * Функция, возвращающая статус запущенного эмулятора
     */
    static String getEmulatorStatus(Object script) {
        return getEmulatorInfo(script, 2).trim()
    }

    /**
     * Функция, возвращающая информацию по заданному индексу о запущенном эмуляторе.
     *
     * Команда "adb devices | grep emulator" возвращает информацию о запущенных эмуляторах в след. формате:
     * emulator-name status
     *
     * Индекс позволяет задать номер необходимого параметра: имя или статус.
     */
    private static String getEmulatorInfo(Object script, Integer index) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAdbHome(script)} devices | grep emulator | head -1 | cut -f$index"
        )
    }

    /**
     * Функция, проверяющая, является ли статус запущенного эмулятора offline
     */
    static Boolean isEmulatorOffline(Object script) {
        return getEmulatorStatus(script) == "offline"
    }
    //endregion

    //region AVD utils
    /**
     * Функция, проверяющая, существует ли AVD с заданным именем
     */
    static String isAvdExists(Object script, String avdName) {
        return getAvdNames(script).find { it == avdName }
    }

    /**
     * Функция, возвращающая список имен AVD
     */
    static String[] getAvdNames(Object script) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAvdManagerHome(script)} list avd | grep Name | awk '{ print \$2 }'"
        ).split()
    }
    //endregion

    //region APK utils
    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом в текущей директории
     */
    static String[] getApkList(Object script, String apkPrefix) {
        return CommonUtil.getShCommandOutput(
                script,
                "find . -name \"*-${apkPrefix}.apk\" | cut -c 3-"
        ).split()
    }

    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом в заданной директории
     */
    static String[] getApkList(Object script, String apkPrefix, String folderName) {
        return CommonUtil.getShCommandOutput(
                script,
                "find \"$folderName\" -name \"*-${apkPrefix}.apk\""
        ).split()
    }

    /**
     * Функция, возвращающая имя директории для APK-файла
     */
    static String getApkFolderName(Object script, String apkFullName) {
        return getApkInfo(script, apkFullName, 1)
    }

    /**
     * Функция, возвращающая краткое имя APK-файла без учета директории
     */
    static String getApkFileName(Object script, String apkFullName) {
        return CommonUtil.getShCommandOutput(
                script,
                "echo \"$apkFullName\" | rev | cut -d '/' -f1 | rev"
        )
    }

    /**
     * Функция, возвращающая префикс для APK-файла.
     */
    static String getApkPrefix(Object script, String apkFileName, AndroidTestConfig config) {
        return CommonUtil.getShCommandOutput(
                script,
                "echo \"$apkFileName\" | awk -F ${getApkSuffix(config)} '{ print \$1 }'"
        ).toString()
    }

    /**
     * Функция, возвращающая имя пакета для приложения, считывая его из манифеста,
     * который можно получить из APK-файла, имя которого передается параметром
     */
    static String getPackageNameFromApk(Object script, String apkFullName, String buildToolsVersion) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAaptHome(script, buildToolsVersion)} dump xmltree \"$apkFullName\" ${ANDROID_MANIFEST_FILE_NAME} \
                            | grep package | cut -d '\"' -f2"
        )
    }

    /**
     * Функция, возвращающая имя модуля, в котором содержится APK-файл.
     *
     * В большинстве случаев, APK-файл находится в папке APK_FOLDER/build,
     * но если проект содержит вложенный многомодульный проект,
     * например, template в android-standard,
     * то имя модуля будет отличаться от имени директории APK-файла.
     */
    static String getApkModuleName(Object script, String apkFullName) {
        return getApkInfo(script, apkFullName, 2)
    }

    /**
     * Функция, возвращающая суффикс для APK-файла
     */
    private static String getApkSuffix(AndroidTestConfig config) {
        return "-${config.testBuildType}-${ANDROID_TEST_APK_SUFFIX}.apk"
    }

    /**
     * Функция, возвращающая информацию по заданному индексу о APK
     */
    private static String getApkInfo(Object script, String apkFullName, Integer index) {
        return CommonUtil.getShCommandOutput(
                script,
                "echo \"$apkFullName\" | cut -d '/' -f${index.toString()}"
        )
    }
    //endregion

    //region Utils for running of instrumental tests
    /**
     * Функция для удаления APK из переиспользуемого эмулятора
     */
    static void uninstallApk(Object script, String emulatorName, String packageName) {
        def trimmedPackageName = packageName.trim()
        // Проверка, был ли установлен APK с заданным именем пакета на текущий эмулятор
        def searchResultCode = script.sh(
                returnStatus: true,
                script: "${getAdbShellCommand(script, emulatorName)} pm list packages | grep $trimmedPackageName"
        )
        if (searchResultCode == 0) {
            script.echo "uninstall previous app $packageName"
            script.sh "${getAdbCommand(script, emulatorName)} uninstall $trimmedPackageName"
        }
    }

    /**
     * Функция для установки APK-файла в заданный пакет
     */
    static void installApk(Object script, String emulatorName, String apkFullName, String apkPackageName) {
        script.sh "${getAdbCommand(script, emulatorName)} \
            push \"${formatArgsForShellCommand(apkFullName)}\" \"${formatArgsForShellCommand(apkPackageName)}\""

        script.sh "${getAdbShellCommand(script, emulatorName)} pm install -t -r ${apkPackageName.trim()}"
    }

    /**
     * Функция для запуска инструментальных тестов
     * @param script контекст вызова
     * @param emulatorName имя эмулятора, на котором будут запущены тесты
     * @param testPackageWithRunner test.package.name/AndroidInstrumentalRunnerName для запуска тестов
     */
    static void runInstrumentalTests(Object script, String emulatorName, String testPackageWithRunner) {
        script.sh "${getAdbShellCommand(script, emulatorName)} \
            am instrument -w -r -e debug false ${formatArgsForShellCommand(testPackageWithRunner)}"
    }

    /**
     * Вспомогательная функция, возвращающая команду для обращения к конкретному девайсу
     */
    private static String getAdbCommand(Object script, String deviceName) {
        return "${CommonUtil.getAdbHome(script)} -s ${deviceName.trim()}"
    }

    /**
     * Вспомогательная функция, возвращающая команду для обращения
     * к командной оболочке конкретного девайса
     */
    private static String getAdbShellCommand(Object script, String deviceName) {
        return "${getAdbCommand(script, deviceName)} shell"
    }
    //endregion

    //region InstrumentationRunner utils
    /**
     * Функция, возвращающая имя gradle task для текущего модуля, префикс которого передается параметром
     */
    static String getInstrumentationGradleTaskRunnerName(String prefix, AndroidTestConfig config) {
        return ":$prefix:${config.instrumentationRunnerGradleTaskName}"
    }

    /**
     * Функция, возвращающая имя testInstrumentationRunner на основе результата после выполнения соотв. gradle task
     * @param gradleOutputFileName имя файла, который содержит результат выполнения gradle-таска
     * @return
     */
    static String getInstrumentationRunnerName(Object script, String gradleOutputFileName) {
        return CommonUtil.getShCommandOutput(
                script,
                "cat $gradleOutputFileName | tail -4 | head -1"
        )
    }
    //endregion

    //region Functions for manipulation of emulator
    /**
     * Функция, выполняющая закрытие запущенного эмулятора
     */
    static void closeRunningEmulator(Object script, AndroidTestConfig config) {
        // Закрытие запущенного эмулятора, если он существует
        def emulatorName = getEmulatorName(script)
        if (CommonUtil.isNotNullOrEmpty(emulatorName)) {
            script.echo "close running emulator"
            script.sh "${CommonUtil.getAdbHome(script)} -s $emulatorName emu kill"
        }
        // Удаление AVD, если необходимо
        if (!config.reuse) {
            script.echo "delete avd"
            script.sh "${CommonUtil.getAvdManagerHome(script)} delete avd -n ${config.avdName} || true"
        }
    }

    /**
     * Функция для создания и запуска нового эмулятора
     */
    static void createAndLaunchNewEmulator(Object script, AndroidTestConfig config) {
        script.echo "create new emulator"
        script.sh "${CommonUtil.getAvdManagerHome(script)} create avd -f \
            -n \"${config.avdName}\" \
            -d \"${config.deviceName}\" \
            -k \"${config.sdkId}\" \
            -c \"${config.sdcardSize}\""
        launchEmulator(script, config)
    }

    /**
     * Функция, выполняющая запуск эмулятора, параметры которого заданы конфигом
     */
    static void launchEmulator(Object script, AndroidTestConfig config) {
        def launchEmulatorCommand = "${CommonUtil.getEmulatorHome(script)} \
                -avd \"${config.avdName}\" \
                -skin \"${config.skinSize}\" -no-window -no-boot-anim "
        launchEmulatorCommand += (config.reuse) ? " &" : " -no-snapshot-save &"
        script.sh launchEmulatorCommand
    }
    //endregion

    //region Helpful functions
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
}

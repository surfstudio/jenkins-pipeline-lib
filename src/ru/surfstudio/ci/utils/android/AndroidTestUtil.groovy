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

    //region Timeouts
    // значение таймаута для создания и загрузки нового эмулятора
    static Integer LONG_TIMEOUT_SECONDS = 20

    // значение таймаута для запуска ранее созданного эмулятора
    static Integer SMALL_TIMEOUT_SECONDS = 7
    //endregion

    //region Emulator utils
    /**
     * Функция, возвращающая имя запущенного эмулятора
     */
    static String getEmulatorName(Object script) {
        return getEmulatorInfo(script, 1)
    }

    /**
     * Функция, возвращающая статус запущенного эмулятора
     */
    static String getEmulatorStatus(Object script) {
        return getEmulatorInfo(script, 2)
    }

    /**
     * Функция, возвращающая информацию по заданному индексу о запущенном эмуляторе
     */
    private static String getEmulatorInfo(Object script, Integer index) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAdbHome(script)} devices | grep emulator | cut -f$index"
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
    static String findAvdName(Object script, String avdName) {
        return getAvdNames(script).find { it == avdName }
    }

    /**
     * Функция, возвращающая список имен AVD
     */
    private def static getAvdNames(Object script) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAvdManagerHome(script)} list avd | grep Name | awk '{ print \$2 }'"
        )
    }
    //endregion

    //region APK utils
    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом
     */
    static String[] getApkList(Object script, String apkPrefix) {
        return CommonUtil.getShCommandOutput(
                script,
                "grep -r --include \"*-${apkPrefix}.apk\" . | cut -d ' ' -f3"
        ).split()
    }

    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом в заданной директории
     */
    static String[] getApkList(Object script, String apkPrefix, String folderName) {
        return CommonUtil.getShCommandOutput(
                script,
                "grep -r --include \"*-${apkPrefix}.apk\" \"$folderName/\" | cut -d ' ' -f3"
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
    static String getPackageNameFromApk(Object script, String apkFullName) {
        return CommonUtil.getShCommandOutput(
                script,
                "aapt dump xmltree \"$apkFullName\" \"${ANDROID_MANIFEST_FILE_NAME}\" \
                            | grep package | cut -d '\"' -f2"
        )
    }

    /**
     * Функция для удаления APK из переиспользуемого эмулятора
     */
    static void uninstallApk(Object script, String emulatorName, String packageName) {
        // Проверка, был ли установлен APK с заданным именем пакета на текущий эмулятор
        def adbCommand = "${CommonUtil.getAdbHome(script)} -s -s $emulatorName"
        def searchResultCode = CommonUtil.getShCommandResultCode(
                script,
                "$adbCommand shell pm list packages | grep $packageName"
        )
        if (searchResultCode == 0) {
            script.echo "uninstall previous app"
            script.sh "$adbCommand uninstall $packageName"
        }
    }

    /**
     * Функция для установки APK-файла в заданный пакет
     */
    static void push(Object script, String emulatorName, String apkFullName, String apkDestPackage) {
        script.sh "${CommonUtil.getAdbHome(script)} -s $emulatorName push $apkFullName $apkDestPackage"
    }

    /**
     * Функция для установка APK, который задается с помощью имени пакета, на эмулятор
     */
    static void installApk(Object script, String emulatorName, String apkPackageName) {
        script.sh "${CommonUtil.getAdbHome(script)} -s $emulatorName shell pm install -t -r $apkPackageName"
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

    //region InstrumentationRunner utils
    /**
     * Функция, возвращающая имя gradle task для текущего модуля, префикс которого передается параметром
     */
    static String getInstrumentationGradleTaskRunnerName(String prefix, AndroidTestConfig config) {
        return ":$prefix:${config.instrumentationRunnerGradleTaskName}"
    }

    /**
     * Функция, возвращающая имя testInstrumentationRunner на основе результата после выполнения соотв. gradle task
     * @param gradleOutputFileName
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
        if (CommonUtil.isNameDefined(emulatorName)) {
            script.echo "close running emulator"
            script.sh "${CommonUtil.getAdbHome(script)} -s \"$emulatorName\" emu kill"
        }
        // Удаление AVD, если необходимо
        if (!config.reuse) {
            script.echo "delete avd"
            script.sh "${CommonUtil.getAvdManagerHome(script)} delete avd -n \"${config.avdName}\""
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
        script.sh(launchEmulatorCommand)
    }
    //endregion
}

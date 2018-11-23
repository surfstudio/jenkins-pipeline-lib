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

import ru.surfstudio.ci.utils.CommonUtil

/**
 * Утилиты для инструментальных тестов Android
 */
class AndroidTestUtil {

    //region Timeouts
    // значение таймаута для создания и загрузки нового эмулятора
    static Integer LONG_TIMEOUT_SECONDS = 20

    // значение таймаута для запуска ранее созданного эмулятора
    static Integer SMALL_TIMEOUT_SECONDS = 7
    //endregion

    //region Названия переменных окружения для инструментальных тестов
    private static String PATH = "PATH"
    private static String ADB_HOME = "ADB_HOME"
    private static String EMULATOR_HOME = "EMULATOR_HOME"
    private static String AVDMANAGER_HOME = "AVDMANAGER_HOME"
    //endregion

    //region Основные shell-команды
    private static String GET_AVD_NAMES_COMMAND = "avdmanager list avd | grep Name | awk '{ print \$2 }'"
    private static String GET_EMULATOR_NAME_COMMAND = "adb devices | grep emulator | cut -f1"
    //endregion

    def static exportAndroidTestEnvironmentVariables(Object script) {
        def androidHome = CommonUtil.getAndroidHome(script)
        CommonUtil.exportEnvironmentVariable(script, ADB_HOME, "$androidHome/platform-tools/adb")
        CommonUtil.exportEnvironmentVariable(script, EMULATOR_HOME, "$androidHome/emulator")
        CommonUtil.exportEnvironmentVariable(script, AVDMANAGER_HOME, "$androidHome/tools/bin")
        CommonUtil.exportEnvironmentVariable(script, PATH, "\$PATH:\$EMULATOR_HOME:\$ADB_HOME:\$AVDMANAGER_HOME")
    }

    def static getAvdNames(Object script) {
        return CommonUtil.getShCommandOutput(script, GET_AVD_NAMES_COMMAND)
    }

    def static getEmulatorName(Object script) {
        return CommonUtil.getShCommandOutput(script, GET_EMULATOR_NAME_COMMAND)
    }

    def static findAvdName(Object script, String avdName) {
        return getAvdNames(script).find { it == avdName }
    }

    def static closeRunningEmulator(Object script, AndroidTestConfig config) {
        // Закрытие запущенного эмулятора, если он существует
        def emulatorName = getEmulatorName(script)
        if (emulatorName != "") {
            script.echo "close running emulator"
            script.sh "adb -s \"$emulatorName\" emu kill"
        }
        // Удаление AVD, если необходимо
        if (!config.stay) {
            script.echo "delete avd"
            script.sh "avdmanager delete avd -n \"${config.avdName}\""
        }
    }

    def static createAndLaunchNewEmulator(Object script, AndroidTestConfig config) {
        script.echo "create new emulator"
        script.sh "avdmanager create avd -f \
            -n \"${config.avdName}\" \
            -d \"${config.deviceName}\" \
            -k \"${config.sdkId}\" \
            -c \"${config.sdcardSize}\""
        launchEmulator(script, config)
    }

    def static launchEmulator(Object script, AndroidTestConfig config) {
        def androidHome = CommonUtil.getAndroidHome(script)
        if (config.stay) {
            script.echo "stay"
            script.sh "$androidHome/emulator/emulator \
                -avd \"${config.avdName}\" \
                -skin \"${config.skinSize}\" -no-window &"
        } else {
            script.echo "not stay"
            script.sh "$androidHome/emulator/emulator \
                -avd \"${config.avdName}\" \
                -skin \"${config.skinSize}\" -no-window -no-snapshot-save &"
        }
    }
}

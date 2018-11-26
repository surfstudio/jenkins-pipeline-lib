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

    private static String EMPTY_STRING = ""

    //region Timeouts
    // значение таймаута для создания и загрузки нового эмулятора
    static Integer LONG_TIMEOUT_SECONDS = 20

    // значение таймаута для запуска ранее созданного эмулятора
    static Integer SMALL_TIMEOUT_SECONDS = 7
    //endregion

    /**
     * Функция, проверяющая, определено ли имя, которое передано параметром
     */
    static Boolean isNameDefined(String emulatorName) {
        return emulatorName != EMPTY_STRING
    }

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

    /**
     * Функция, выполняющая закрытие запущенного эмулятора
     */
    static void closeRunningEmulator(Object script, AndroidTestConfig config) {
        // Закрытие запущенного эмулятора, если он существует
        def emulatorName = getEmulatorName(script)
        if (isNameDefined(emulatorName)) {
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
        launchEmulatorCommand+=(config.reuse) ? " &" : " -no-snapshot-save &"
        script.sh(launchEmulatorCommand)
    }
}

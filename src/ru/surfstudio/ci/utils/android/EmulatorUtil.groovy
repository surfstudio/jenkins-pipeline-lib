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
 * Утилиты для работы с эмулятором
 */
class EmulatorUtil {

    // значение таймаута для создания и загрузки нового эмулятора
    static Integer EMULATOR_TIMEOUT = 5

    /**
     * Функция, возвращающая имя последнего запущенного эмулятора
     */
    static String getEmulatorName(Object script) {
        return getEmulatorInfo(script, 1).trim()
    }

    /**
     * Функция, возвращающая статус последнего запущенного эмулятора
     */
    static String getEmulatorStatus(Object script) {
        return getEmulatorInfo(script, 2).trim()
    }

    /**
     * Функция, возвращающая статус эмулятора с заданным именем
     */
    static String getEmulatorStatus(Object script, String emulatorName) {
        return script.sh(
                returnStdout: true,
                script: "${CommonUtil.getAdbHome(script)} devices | grep $emulatorName | cut -f2"
        ).trim()
    }

    /**
     * Функция, возвращающая информацию по заданному индексу о последнем запущенном эмуляторе.
     *
     * Команда "adb devices | grep emulator" возвращает информацию о запущенных эмуляторах в след. формате:
     * emulator-name status
     *
     * Индекс позволяет задать номер необходимого параметра: имя или статус.
     */
    private static String getEmulatorInfo(Object script, Integer index) {
        return script.sh(
                returnStdout: true,
                script: "${CommonUtil.getAdbHome(script)} devices | grep emulator | head -1 | cut -f$index"
        )
    }

    /**
     * Функция, проверяющая, является ли статус последнего запущенного эмулятора offline
     */
    static Boolean isEmulatorOffline(Object script) {
        return getEmulatorStatus(script) == "offline"
    }

    /**
     * Функция, проверяющая, является ли статус эмулятора с заданным именем offline
     */
    static Boolean isEmulatorOffline(Object script, String emulatorName) {
        return getEmulatorStatus(script, emulatorName) == "offline"
    }

    //region Functions for manipulation of emulator
    /**
     * Функция, выполняющая закрытие запущенного эмулятора
     */
    static void closeRunningEmulator(Object script, AvdConfig config) {
        // Закрытие запущенного эмулятора, если он существует
        if (CommonUtil.isNotNullOrEmpty(config.emulatorName)) {
            script.echo "close running emulator"
            script.sh "${CommonUtil.getAdbHome(script)} -s ${config.emulatorName} emu kill"
        }
        script.echo "delete avd"
        script.sh "${CommonUtil.getAvdManagerHome(script)} delete avd -n ${config.avdName} || true"
    }

    /**
     * Функция для создания и запуска нового эмулятора
     */
    static void createAndLaunchNewEmulator(Object script, AvdConfig config) {
        script.echo "create new emulator"
        script.sh "${CommonUtil.getAvdManagerHome(script)} create avd -f \
            -n \"${config.avdName}\" \
            -d \"${config.deviceName}\" \
            -k \"${config.sdkId}\" \
            -c \"${config.sdcardSize}\""
        launchEmulator(script, config)
    }

    /**
     * Функция, выполняющая запуск эмулятора, параметры которого заданы конфигом,
     * и запоминающая имя запущенного эмулятора.
     */
    static void launchEmulator(Object script, AvdConfig config) {
        script.sh "${CommonUtil.getEmulatorHome(script)} \
                -avd \"${config.avdName}\" \
                -skin \"${config.skinSize}\" \
                -no-window -no-boot-anim -no-snapshot-save &"
        sleep(script, EMULATOR_TIMEOUT)
        // запоминаем новое имя эмулятора
        config.emulatorName = getEmulatorName(script)
    }

    /**
     * Вспомогательная функция, закрывающая запущенный эмулятор и создающая новый
     */
    static void closeAndCreateEmulator(Object script, AvdConfig config, String message) {
        script.echo message
        closeRunningEmulator(script, config)
        createAndLaunchNewEmulator(script, config)
    }
    //endregion

    private static void sleep(Object script, Integer timeout) {
        if (timeout > 0) {
            script.echo "waiting $timeout seconds..."
            script.sh "sleep $timeout"
        }
    }
}

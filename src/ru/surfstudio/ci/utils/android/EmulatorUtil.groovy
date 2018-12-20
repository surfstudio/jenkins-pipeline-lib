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
        return script.sh(
                returnStdout: true,
                script: "${CommonUtil.getAdbHome(script)} devices | grep emulator | head -1 | cut -f$index"
        )
    }

    /**
     * Функция, проверяющая, является ли статус запущенного эмулятора offline
     */
    static Boolean isEmulatorOffline(Object script) {
        return getEmulatorStatus(script) == "offline"
    }

    //region Functions for manipulation of emulator
    /**
     * Функция, выполняющая закрытие запущенного эмулятора
     */
    static void closeRunningEmulator(Object script, AvdConfig config) {
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
     * Функция, выполняющая запуск эмулятора, параметры которого заданы конфигом
     */
    static void launchEmulator(Object script, AvdConfig config) {
        def launchEmulatorCommand = "${CommonUtil.getEmulatorHome(script)} \
                -avd \"${config.avdName}\" \
                -skin \"${config.skinSize}\" -no-window -no-boot-anim "
        launchEmulatorCommand += (config.reuse) ? " &" : " -no-snapshot-save &"
        script.sh launchEmulatorCommand
    }
    //endregion
}

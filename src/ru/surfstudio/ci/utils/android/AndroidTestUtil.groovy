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

    //region Timeouts
    // значение таймаута для создания и загрузки нового эмулятора
    static Integer LONG_TIMEOUT_SECONDS = 20

    // значение таймаута для запуска ранее созданного эмулятора
    static Integer SMALL_TIMEOUT_SECONDS = 7
    //endregion

    /**
     * Функция, возвращающая список имен AVD
     */
    def static getAvdNames(Object script) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAvdManagerHome(script)} list avd | grep Name | awk '{ print \$2 }'"
        )
    }

    /**
     * Функция, возвращающая имя запущенного эмулятора
     */
    def static getEmulatorName(Object script) {
        return CommonUtil.getShCommandOutput(
                script,
                "${CommonUtil.getAdbHome(script)} devices | grep emulator | cut -f1"
        )
    }

    /**
     * Функция, проверяющая, существует ли AVD с заданным именем
     */
    def static findAvdName(Object script, String avdName) {
        return getAvdNames(script).find { it == avdName }
    }

    /**
     * Функция, выполняющая закрытие запущенного эмулятора
     */
    def static closeRunningEmulator(Object script, AndroidTestConfig config) {
        // Закрытие запущенного эмулятора, если он существует
        def emulatorName = getEmulatorName(script)
        if (emulatorName != "") {
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
    def static createAndLaunchNewEmulator(Object script, AndroidTestConfig config) {
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
    def static launchEmulator(Object script, AndroidTestConfig config) {
        def launchEmulatorCommand = "${CommonUtil.getEmulatorHome(script)} \
                -avd \"${config.avdName}\" \
                -skin \"${config.skinSize}\" -no-window -no-boot-anim "
        launchEmulatorCommand+=(config.reuse) ? " &" : " -no-snapshot-save &"
        script.sh(launchEmulatorCommand)
    }
}

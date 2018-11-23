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

    // значение таймаута для создания и загрузки нового эмулятора
    Integer longTimeoutSeconds = 20

    // значение таймаута для запуска ранее созданного эмулятора
    Integer smallTimeoutSeconds = 7

    //region Названия переменных окружения для инструментальных тестов

    private static String ADB_HOME = "ADB_HOME"
    private static String EMULATOR_HOME = "EMULATOR_HOME"
    private static String AVDMANAGER_HOME = "AVDMANAGER_HOME"

    //endregion

    def static exportAndroidTestEnvironmentVariables(Object script) {
        def androidHome = CommonUtil.getAndroidHome(script)
        CommonUtil.exportEnvironmentVariable(script, ADB_HOME, "$androidHome/platform-tools/adb")
        CommonUtil.exportEnvironmentVariable(script, EMULATOR_HOME, "$androidHome/emulator")
        CommonUtil.exportEnvironmentVariable(script, AVDMANAGER_HOME, "$androidHome/tools/bin")
    }

    def static getAvdNames(Object script) {
        return CommonUtil.getShCommandOutput(script, "avdmanager list avd | grep Name | awk '{ print \$2 }'")
    }

    def static getEmulatorName(Object script) {

    }

    def static findAvdName(Object script, String avdName) {
        return getAvdNames(script).find { it == avdName }
    }
}

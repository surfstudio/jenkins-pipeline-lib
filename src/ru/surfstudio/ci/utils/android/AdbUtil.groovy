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
 * Утилиты для работы с ADB
 */
class AdbUtil {

    /**
     * Функция, возвращающая команду для обращения к конкретному девайсу
     */
    static String getAdbCommand(Object script, String deviceName) {
        return "${CommonUtil.getAdbHome(script)} -s ${deviceName.trim()}"
    }

    /**
     * Функция, возвращающая команду для обращения к командной оболочке конкретного девайса
     */
    static String getAdbShellCommand(Object script, String deviceName) {
        return "${getAdbCommand(script, deviceName)} shell"
    }
}

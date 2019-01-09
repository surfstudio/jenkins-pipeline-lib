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
package ru.surfstudio.ci.utils.android.config

/**
 * Класс, содержащий параметры эмулятора для запуска инструментальных тестов
 */
class AvdConfig {

    // имя AVD
    String avdName = "avd-androidTest"

    // имя девайса для запуска AVD
    String deviceName = "Nexus 5X"

    // SDK ID для запуска эмулятора
    String sdkId = "system-images;android-28;google_apis;x86_64"

    // размер объема sdcard эмулятора
    String sdcardSize = "3072M"

    // разрешение эмулятора
    String skinSize = "1440x2560"
}

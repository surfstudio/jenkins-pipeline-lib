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
}

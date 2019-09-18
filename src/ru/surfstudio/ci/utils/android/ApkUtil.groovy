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
 * Утилиты для работы с APK
 */
class ApkUtil {

    private static String ANDROID_MANIFEST_FILE_NAME = "AndroidManifest.xml"
    private static String UNSIGNED_APK_PREFIX = "unsigned"

    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом в текущей директории
     */
    static String[] getApkList(Object script, String apkPrefix) {
        return getShCommandOutput(
                script,
                "${getCommandForApkSearching(".", apkPrefix)} | cut -c 3-"
        ).split()
    }

    static String[] getModuleList(Object script) {
        return getShCommandOutput(
                script,
                "./gradlew -q projects"
        ).split()
    }

    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом в заданной директории
     */
    static String[] getApkList(Object script, String apkPrefix, String folderName) {
        return getShCommandOutput(
                script,
                getCommandForApkSearching(folderName, apkPrefix)
        ).split()
    }

    /**
     * Функция, возвращающая список APK-файлов с заданным суффиксом в заданной директории,
     * предоставляющая возможность фильтра найденных APK-файлов:
     * исключить те файлы, которые содержат конкретный префикс.
     */
    static String[] getApkList(Object script, String searchedApkPrefix, String excludedApkPrefix, String folderName) {
        return getShCommandOutput(
                script,
                "${getCommandForApkSearching(folderName, searchedApkPrefix)} ! -name \"*-${excludedApkPrefix}.apk\""
        ).split()
    }

    /**
     * Функция, возвращающая строку команды для поиска APK с заданным префиксом в заданной директории
     * @param folderName директория для поиска APK
     * @param apkPrefix префикс для поиска APK-файлов
     * @param excludeUnsigned флаг, показывающий, нужно ли исключать из поиска неподписанные APK
     * @return строка команды для поиска APK с заданным префиксом в заданной директории
     */
    private static String getCommandForApkSearching(
            String folderName,
            String apkPrefix,
            Boolean excludeUnsigned = true
    ) {
        String baseCommand = "find $folderName -name \"*-${apkPrefix}.apk\""
        return excludeUnsigned ? "$baseCommand ! -name \"*-${UNSIGNED_APK_PREFIX}.apk\"" : baseCommand
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
        return getShCommandOutput(
                script,
                "echo \"$apkFullName\" | rev | cut -d '/' -f1 | rev"
        )
    }

    /**
     * Функция, возвращающая имя пакета для приложения, считывая его из манифеста,
     * который можно получить из APK-файла, имя которого передается параметром
     */
    static String getPackageNameFromApk(Object script, String apkFullName, String buildToolsVersion) {
        return getShCommandOutput(
                script,
                "${CommonUtil.getAaptHome(script, buildToolsVersion)} dump xmltree \"$apkFullName\" ${ANDROID_MANIFEST_FILE_NAME} \
                            | grep package | cut -d '\"' -f2"
        )
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
     * Функция для удаления APK из переиспользуемого эмулятора
     */
    static void uninstallApk(Object script, String emulatorName, String packageName) {
        def trimmedPackageName = packageName.trim()
        // Проверка, был ли установлен APK с заданным именем пакета на текущий эмулятор
        def searchResultCode = script.sh(
                returnStatus: true,
                script: "${AdbUtil.getAdbShellCommand(script, emulatorName)} pm list packages | grep $trimmedPackageName"
        )
        if (searchResultCode == 0) {
            script.echo "uninstall previous app $packageName"
            script.sh "${AdbUtil.getAdbCommand(script, emulatorName)} uninstall $trimmedPackageName"
        }
    }

    /**
     * Функция для установки APK-файла
     */
    static void installApk(Object script, String emulatorName, String apkFullName) {
        def tempApkFileName = UUID.randomUUID().toString()
        script.sh "${AdbUtil.getAdbCommand(script, emulatorName)} \
            push \"${formatArgsForShellCommand(apkFullName)}\" \"$tempApkFileName\""

        script.sh "${AdbUtil.getAdbShellCommand(script, emulatorName)} pm install -t -r $tempApkFileName"
    }

    /**
     * Функция, возвращающая информацию по заданному индексу об имени APK-файла.
     *
     * Параметром передается полный путь к APK, который может иметь вид
     * module/build/outputs/.../name.apk или module/submodule/build/outputs/.../name.apk
     *
     * Для проектов, содержащих дополнительные модули, может потребоваться имя модуля, которое идет после имени проекта
     * и которое можно получить по индексу.
     */
    private static String getApkInfo(Object script, String apkFullName, Integer index) {
        return getShCommandOutput(
                script,
                "echo \"$apkFullName\" | cut -d '/' -f${index.toString()}"
        )
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

    private def static getShCommandOutput(Object script, String command) {
        return script.sh(returnStdout: true, script: command)
    }
}

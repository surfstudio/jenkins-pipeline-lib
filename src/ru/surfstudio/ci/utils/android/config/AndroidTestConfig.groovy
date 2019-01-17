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
 * Вспомогательный класс, содержащий параметры конфигурации запуска инструментальных тестов
 */
class AndroidTestConfig {

    // имя gradle-тска для сборки APK для инструментальных тестов
    String instrumentalTestAssembleGradleTask

    // путь, в котором будут храниться отчеты о тестах в xml-формате
    String instrumentalTestResultPathDirXml

    // путь, в котором будут храниться отчеты о тестах в html-формате
    String instrumentalTestResultPathDirHtml

    // флаг, показывающий, должно ли имя AVD быть уникальным для текущего job'a
    Boolean generateUniqueAvdNameForJob

    // количество попыток перезапуска тестов для одного модуля при падении одного из них
    Integer instrumentationTestRetryCount

    AndroidTestConfig(
            String instrumentalTestAssembleGradleTask,
            String instrumentalTestResultPathDirXml,
            String instrumentalTestResultPathDirHtml,
            Boolean generateUniqueAvdNameForJob,
            Integer instrumentationTestRetryCount
    ) {
        this.instrumentalTestAssembleGradleTask = instrumentalTestAssembleGradleTask
        this.instrumentalTestResultPathDirXml = instrumentalTestResultPathDirXml
        this.instrumentalTestResultPathDirHtml = instrumentalTestResultPathDirHtml
        this.generateUniqueAvdNameForJob = generateUniqueAvdNameForJob
        this.instrumentationTestRetryCount = instrumentationTestRetryCount
    }
}

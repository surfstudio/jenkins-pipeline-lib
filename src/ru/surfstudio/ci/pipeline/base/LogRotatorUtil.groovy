/*
  Copyright (c) 2020-present, SurfStudio LLC.

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
package ru.surfstudio.ci.pipeline.base

import ru.surfstudio.ci.pipeline.ScmPipeline

/**
 * Утилиты для [LogRotator]
 */
class LogRotatorUtil {

    public static String ARTIFACTS_DAYS_TO_KEEP_NAME = "artifactDaysToKeep"
    public static String ARTIFACTS_NUM_TO_KEEP_NAME = "artifactNumToKeep"
    public static String DAYS_TO_KEEP_NAME = "daysToKeep"
    public static String NUM_TO_KEEP_NAME = "numToKeep"

    /**
     * Функция, возвращающая безопасное значение параметра
     */
    static String getActualParameterValue(script, String paramName, int currentValue, int maxValue) {
        if (currentValue > maxValue) {
            script.echo "WARNING: $currentValue for $paramName is ignored, using maxValue=$maxValue"
            return maxValue.toString()
        }
        return currentValue.toString()
    }
}

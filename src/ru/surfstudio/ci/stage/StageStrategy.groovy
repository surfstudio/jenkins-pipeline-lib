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
package ru.surfstudio.ci.stage

class StageStrategy implements Serializable {
    public static final String SKIP_STAGE = "SKIP_STAGE"
    public static final String FAIL_WHEN_STAGE_ERROR = "FAIL_WHEN_STAGE_ERROR"
    public static final String UNSTABLE_WHEN_STAGE_ERROR = "UNSTABLE_WHEN_STAGE_ERROR"
    public static final String SUCCESS_WHEN_STAGE_ERROR = "SUCCESS_WHEN_STAGE_ERROR"
    public static final String UNDEFINED = "UNDEFINED"
}
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
package ru.surfstudio.ci.utils.flutter

import ru.surfstudio.ci.utils.YamlUtil

class FlutterUtil {

    @Deprecated //use YamlUtil
    static String getYamlVariable(Object script, String file, String varName) {
        return YamlUtil.getYamlVariable(script, file, varName)
    }

    @Deprecated //use YamlUtil
    static String changeYamlVariable(Object script, String file, String varName, String newVarValue) {
        YamlUtil.changeYamlVariable(script, file, varName, newVarValue)
    }

    static String getVersionName(String compositeVersion) {
        compositeVersion.split(/(\+)/)[0]
    }

    static String getVersionCode(String compositeVersion) {
        compositeVersion.split(/(\+)/)[1]
    }
}

/*
  Copyright (c) 2021-present, SurfStudio LLC.

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
package ru.surfstudio.ci.pipeline.spa

import ru.surfstudio.ci.NodeProvider

class SPAPipelineAndroid extends SPAPipeline {

    public cpdGradleTask = "clean cpdCheck"
    public cpdReportsDir = "build/reports/cpd/**"

    SPAPipelineAndroid(Object script) {
        super(script)
    }

    def init() {
        node = NodeProvider.androidNode

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECKOUT, false) {
                    standardCheckoutStageBody()
                },
                stage(CPD_CHECK) {
                    script.sh "./gradlew $cpdGradleTask"
                    script.step([$class: 'ArtifactArchiver', artifacts: cpdReportsDir, allowEmptyArchive: true])
                }
        ]

        finalizeBody = { finalizeStageBody(this) }
    }
}

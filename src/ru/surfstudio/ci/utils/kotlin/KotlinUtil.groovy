package ru.surfstudio.ci.utils.kotlin

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.utils.android.AndroidUtil

class KotlinUtil {
    private KotlinUtil() {
    }
    /**
     * Форматирование исходного кода на котлин
     */
    def static codeFormatStage(
            Object script,
            String sourceBranch,
            String destinationBranch
    ) {
        def files = RepositoryUtil.ktFilesDiffPr(script, sourceBranch, destinationBranch)
        if (CommonUtil.isEmptyStringArray(files)) {
            script.echo "No *.kt files for formatting."
            return
        }
        try {
            AndroidUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew ktlintFilesFormat -PlintFiles=\"${files.join("\",\"")}\""
            }
        } catch (Exception ex) {
            script.echo "Formatting exception $ex"
        }
    }
}

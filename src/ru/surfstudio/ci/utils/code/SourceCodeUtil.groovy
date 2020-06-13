package ru.surfstudio.ci.utils.code

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.utils.buildsystems.GradleUtil

class SourceCodeUtil {
    private SourceCodeUtil() {
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
            GradleUtil.withGradleBuildCacheCredentials(script) {
                script.sh "./gradlew ktlintFilesFormat -PlintFiles=\"${files.join("\",\"")}\""
            }
        } catch (Exception ex) {
            script.echo "Formatting exception $ex"
        }
    }

    static boolean checkChangesAndUpdate(
            Object script,
            String repoUrl,
            String repoCredentialsId,
            String sourceBranch
    ) {
        boolean hasChanges = RepositoryUtil.checkHasChanges(script)
        if (hasChanges) {
            RepositoryUtil.notifyGitlabAboutStageAborted(script, repoUrl, RepositoryUtil.SYNTHETIC_PIPELINE_STAGE, sourceBranch)

            String jiraIssueKey
            try {
                jiraIssueKey = "\nApplyed for jira issue: ${(RepositoryUtil.getCurrentCommitMessage(script) =~ JIRA_ISSUE_KEY_PATTERN)[0][0]}."
            } catch (Exception ignored) {
                jiraIssueKey = ""
            }
            String commitHash = RepositoryUtil.getCurrentCommitHash(script).toString().take(8)

            script.sh "git commit -a -m \"Code Formatting $RepositoryUtil.SKIP_CI_LABEL1." + jiraIssueKey + "\nLast formatted commit is $commitHash \""
            RepositoryUtil.push(script, repoUrl, repoCredentialsId)
        } else {
            script.echo "No modification after code formatting."
        }
        return hasChanges
    }
}

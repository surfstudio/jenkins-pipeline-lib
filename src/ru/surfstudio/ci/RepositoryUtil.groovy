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
package ru.surfstudio.ci

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.IdCredentials
import org.apache.tools.ant.types.selectors.SelectSelector
import org.eclipse.jgit.transport.URIish
import org.jenkinsci.plugins.gitclient.Git

class RepositoryUtil {

    def static SKIP_CI_LABEL1 = "[skip ci]"
    def static SKIP_CI_LABEL2 = "[ci skip]"
    def static VERSION_LABEL1 = "[version]"
    def static DEFAULT_GITLAB_CONNECTION = "Gitlab Surf"
    def static SYNTHETIC_PIPELINE_STAGE = "Pipeline"

    def static notifyGitlabAboutStageStart(Object script, String repoUrl, String stageName){
        def gitlabStatus = "running"
        def slug = getCurrentGitlabRepoSlug(script, repoUrl)
        def commit = getSavedGitCommitHash(script)
        if (!commit) {
            script.error("You must call RepositoryUtil.saveCurrentGitCommitHash() before invoke this method")
        }
        script.echo "Notify GitLab - stage: $stageName, repoSlug: $slug, commitId: $commit, status: $gitlabStatus"
        script.updateGitlabCommitStatus(name: "$stageName", state: "$gitlabStatus", builds: [[projectId: "$slug", revisionHash: "$commit"]])
    }

    def static notifyGitlabAboutStageFinish(Object script, String repoUrl, String stageName, String result){
        def gitlabStatus = ""

        switch (result) {
            case Result.SUCCESS:
                gitlabStatus = "success"
                break
            case Result.ABORTED:
                gitlabStatus = "canceled"
                break
            case Result.FAILURE:
            case Result.UNSTABLE:
                gitlabStatus = "failed"
                break
            default:
                script.error "Unsupported Result: ${result}"
        }
        def commit = getSavedGitCommitHash(script)
        def slug = getCurrentGitlabRepoSlug(script, repoUrl)
        if (!commit) {
            script.error("You must call RepositoryUtil.saveCurrentGitCommitHash() before invoke this method")
        }
        script.echo "Notify GitLab - stage: $stageName, repoSlug: $slug, commitId: $commit, status: $result"
        script.updateGitlabCommitStatus(name: "$stageName", state: "$gitlabStatus", builds: [[projectId: "$slug", revisionHash: "$commit"]])
    }

    def static notifyGitlabAboutStageAborted(Object script, String repoUrl, String stageName, String sourceBranch){
        def gitlabStatus = "aborted"
        def slug = getCurrentGitlabRepoSlug(script, repoUrl)
        script.echo "Notify GitLab - synthetic stage: $stageName, repoSlug: $slug, branch: $sourceBranch, status: $gitlabStatus"
        script.updateGitlabCommitStatus(name: "$stageName", state: "$gitlabStatus", builds: [[projectId: "$slug", revisionHash: "$sourceBranch"]])
    }

    def static notifyGitlabAboutStagePending(Object script, String repoUrl, String stageName, String sourceBranch){
        def gitlabStatus = "pending"
        def slug = getCurrentGitlabRepoSlug(script, repoUrl)
        script.echo "Notify GitLab - synthetic stage: $stageName, repoSlug: $slug, branch: $sourceBranch, status: $gitlabStatus"
        script.updateGitlabCommitStatus(name: "$stageName", state: "$gitlabStatus", builds: [[projectId: "$slug", revisionHash: "$sourceBranch"]])
    }

    def static notifyBitbucketAboutStageStart(Object script, String repoUrl, String stageName){
        def bitbucketStatus = 'INPROGRESS'
        def slug = getCurrentBitbucketRepoSlug(script, repoUrl)
        def commit = getSavedGitCommitHash(script)
        if (!commit) {
            script.error("You must call RepositoryUtil.saveCurrentGitCommitHash() before invoke this method")
        }
        script.echo "Notify bitbucket - stage: $stageName, repoSlug: $slug, commitId: $commit, status: $bitbucketStatus"
        script.bitbucketStatusNotify(
                buildState: 'INPROGRESS',
                buildKey: stageName,
                buildName: stageName,
                repoSlug: slug,
                commitId: commit
        )
    }

    def static notifyBitbucketAboutStageFinish(Object script, String repoUrl, String stageName, String result){
        def bitbucketStatus = ""

        switch (result){
            case Result.SUCCESS:
                bitbucketStatus = 'SUCCESSFUL'
                break
            case Result.ABORTED:
                bitbucketStatus = 'STOPPED'
                break
            case Result.FAILURE:
            case Result.UNSTABLE:
                bitbucketStatus = 'FAILED'
                break
            default:
                script.error "Unsupported Result: ${result}"
        }
        def slug = getCurrentBitbucketRepoSlug(script, repoUrl)
        def commit = getSavedGitCommitHash(script)
        if (!commit) {
            script.error("You must call RepositoryUtil.saveCurrentGitCommitHash() before invoke this method")
        }
        script.echo "Notify bitbucket - stage: $stageName, repoSlug: $slug, commitId: $commit, status: $bitbucketStatus"
        script.bitbucketStatusNotify(
                buildState: bitbucketStatus,
                buildKey: stageName,
                buildName: stageName,
                repoSlug: slug,
                commitId: commit
        )
    }

    def static getCurrentGitlabRepoSlug(Object script, String repoUrl){
        def splittedUrlString = ""
        def splittedUrlArray = repoUrl.split("/")
        for (def i = 3; i < splittedUrlArray.length; i++)
            if(i == splittedUrlArray.length - 1) {
                splittedUrlString += splittedUrlArray[i]
            }
            else {
                splittedUrlString += splittedUrlArray[i]
                splittedUrlString += "/"
            }
        return splittedUrlString
    }

    def static getCurrentBitbucketRepoSlug(Object script, String repoUrl){
        def splittedUrl = repoUrl.split("/")
        return splittedUrl[splittedUrl.length - 1]
    }

    /**
     * call this after checkout for save source commit hash
     */
    def static saveCurrentGitCommitHash(Object script) {
        script.env.COMMIT_HASH = getCurrentCommitHash(script)
        script.echo "Set global variable COMMIT_HASH to $script.env.COMMIT_HASH"
    }

    def static getCurrentCommitHash(script) {
        return script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    }

    /**
     * @return saved commit hash via method saveCurrentGitCommitHash
     */
    def static getSavedGitCommitHash(Object script) {
        return script.env.COMMIT_HASH
    }

    /**
     * Extract remote repository config, if pipeline places in Jenkinsfile in repo and pass it to handler
     * @param script
     * @param handler gets two parameters (url, credentialsId)
     */
    def static tryExtractInitialRemoteConfig(Object script, Closure handler) {
        try {
            def config = script.scm.userRemoteConfigs.first()
            script.echo "Extracted initial repoUrl: $config.url, repoCredentialsId: $config.credentialsId"
            handler(config.url, config.credentialsId)
        } catch (e){
            script.echo "Cannot extract initial repository remote config: ${e.toString()}"
        }
    }

    def static Collection<String> getRefsForCurrentCommitMessage(Object script){
        def String rawRefs = script.sh(script: "git log -1 --pretty=%D", returnStdout: true)
        def result = rawRefs.split(/(->|, |\n)/).findAll({it?.trim()})
        script.echo "extracted refs for current commit message: $result"
        return result
    }

    def static String getCurrentCommitMessage(Object script){
        def message = script.sh(script: "git log -1 --pretty=%B", returnStdout: true)
        script.echo "extracted current commit message: $message"
        return message
    }

    def static isContainsSkipCi(String text){
        return text.contains(SKIP_CI_LABEL1) || text.contains(SKIP_CI_LABEL2)
    }

    def static isCurrentCommitMessageContainsSkipCiLabel(Object script){
        return isContainsSkipCi(getCurrentCommitMessage(script))
    }

    def static isCurrentCommitMessageContainsVersionLabel(Object script){
        return getCurrentCommitMessage(script).contains(VERSION_LABEL1)
    }

    def static setDefaultJenkinsGitUser(Object script) {
        script.sh 'git config --global user.name "Surf_Builder"'
        script.sh 'git config --global user.email "jenkins@surfstudio.ru"'
    }

    def static push(Object script, String repoUrl, String repoCredentialsId) {
        def gitClient = prepareGitClient(script, repoCredentialsId)
        gitClient.push()
                .to(new URIish(repoUrl))
                .execute()
    }

    def static pushForceTag(Object script, String repoUrl, String repoCredentialsId) {
        def gitClient = prepareGitClient(script, repoCredentialsId)
        gitClient.push()
                .to(new URIish(repoUrl))
                .force()
                .tags(true)
                .execute()
    }

    def static prepareGitClient(Object script, String repoCredentialsId) {
        def gitClient = Git
                .with(
                script.getContext(hudson.model.TaskListener),
                script.getContext(hudson.EnvVars))
                .in(script.getContext(hudson.FilePath))
                .using("git")
                .getClient()
        def cred = CredentialsProvider.findCredentialById(repoCredentialsId, IdCredentials.class, script.currentBuild.rawBuild);
        gitClient.addDefaultCredentials(cred)
        return gitClient
    }

    def static checkLastCommitMessageContainsSkipCiLabel(Object script){
        script.echo "Checking $RepositoryUtil.SKIP_CI_LABEL1 label in last commit message for automatic builds"
        if (isCurrentCommitMessageContainsSkipCiLabel(script) && !CommonUtil.isJobStartedByUser(script)){
            throw new InterruptedException("Job aborted, because it triggered automatically and last commit message contains $RepositoryUtil.SKIP_CI_LABEL1 label")
        }
    }
    static checkHasChanges(Object script) {
        return !script.sh(returnStdout: true, script: "git status --porcelain --untracked-files=no").isEmpty()
    }

    static String[] ktFilesDiffPr(
            Object script,
            String sourceBranch,
            String destinationBranch
    ) {
        return script.sh(returnStdout: true, script: "git log --no-merges --first-parent --oneline --name-only origin/${destinationBranch}..${sourceBranch} | grep '.kt' || true")
                .split("\n")
    }

    static revertUncommittedChanges(
            Object script
    ) {
        script.sh "git reset"
        script.sh "git checkout ."
        script.sh "git clean -fdx"
    }
}

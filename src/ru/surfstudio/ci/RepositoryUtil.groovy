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

class RepositoryUtil {

    def static SKIP_CI_LABEL1 = "[skip ci]"
    def static SKIP_CI_LABEL2 = "[ci skip]"
    def static VERSION_LABEL1 = "[version]"

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

    def static getCurrentBitbucketRepoSlug(Object script, String repoUrl){
        def splittedUrl = repoUrl.split("/")
        return splittedUrl[splittedUrl.length - 1]
    }

    /**
     * call this after checkout for save source commit hash
     */
    def static saveCurrentGitCommitHash(Object script) {
        script.env.COMMIT_HASH = script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
        script.echo "Set global variable COMMIT_HASH to $script.env.COMMIT_HASH"
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

    def static String[] getRefsForCurrentCommitMessage(Object script){
        def String rawRefs = script.sh(script: "git log -1 --pretty=%D", returnStdout: true)
        String result = rawRefs.split(/(, | -> |)/)
        script.echo "extracted refs for current commit message: $rawRefs"
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

    def static setRemoteOriginUrlWithUsername(Object script, String url, String credentialsId) { //todo support only clear https bitbucket url and usernamePassword credentials now (e.g. https://bitbucket.org/surfstudio/android-standard )
        script.withCredentials([script.usernamePassword(credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            def newUrl = url.replace("https://", "https://$script.USERNAME@")
            script.sh "git remote set-url origin $newUrl"
        }

    }
}

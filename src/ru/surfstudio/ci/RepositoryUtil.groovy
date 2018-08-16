package ru.surfstudio.ci

class RepositoryUtil {

    def static notifyBitbucketAboutStageStart(Object script, String stageName){
        def bitbucketStatus = 'INPROGRESS'
        def slug = getCurrentBitbucketRepoSlug(script)
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

    def static notifyBitbucketAboutStageFinish(Object script, String stageName, String result){
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
        def slug = getCurrentBitbucketRepoSlug(script)
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

    def static getCurrentBitbucketRepoSlug(Object script){
        def String url = script.scm.userRemoteConfigs[0].url
        def splittedUrl = url.split("/")
        return splittedUrl[splittedUrl.length - 1]
    }

    /**
     * call this after checkout for save source commit hash
     */
    def static saveCurrentGitCommitHash(Object script, String gitDir='') {
        script.dir(gitDir) {
            script.env.COMMIT_HASH = script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
            script.echo "Set global variable COMMIT_HASH to $script.env.COMMIT_HASH"
        }
    }

    /**
     * @return saved commit hash via method saveCurrentGitCommitHash
     */
    def static getSavedGitCommitHash(Object script) {
        return script.env.COMMIT_HASH
    }

    /**
     * @param script
     * @param handler gets two parameters (url, credentialsId)
     */
    def static tryExtractRemoteConfig(Object script, Closure handler) {
        try {
            def config = script.scm.userRemoteConfigs[0]
            handler(config.url, config.credentialsId)
        } catch (e){
            script.echo "Cannot extract repository remote config: ${e.toString()}"
        }
    }
}

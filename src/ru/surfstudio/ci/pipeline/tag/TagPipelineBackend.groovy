package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.pipeline.helper.DockerHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.backend.BackendUtil

import static ru.surfstudio.ci.CommonUtil.extractValueFromEnvOrParamsAndRun

class TagPipelineBackend extends TagPipeline {
    public buildGradleTask = "clean assemble"
    public unitTestGradleTask = "test"
    public DOCKER_BUILD_WRAPPED_STAGES = "Executing stages inside docker"
    public dockerImageForBuild = "gradle:6.0.1-jdk11"
    public dockerArguments = null
    public DOCKER_BUILD_PUBLISH_IMAGE = "Build and publish docker image"


    public unitTestResultPathXml = "build/test-results/test/*.xml"
    public unitTestResultDirHtml = "build/reports/tests/test"
    public pathToDockerfile = "./"
    public registryUrl = "eu.gcr.io"
    public registryPathAndProjectId = ""

    public gradleBuildFile = "build.gradle.kts"
    public appVersionNameGradleVar = "appVersionName"
    public appVersionCodeGradleVar = "appVersionCode"

    private isStaging = false

    TagPipelineBackend(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getBackendNode()

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = { initBody(this) }
        propertiesProvider = { properties(this) }

        extractValueFromEnvOrParamsAndRun(script, REPO_TAG_PARAMETER) {
            value -> isStaging = value.toLowerCase().contains("staging") || value.toLowerCase().contains("snapshot") || value.toLowerCase().contains("dev")
        }

        stages = [
                docker(DOCKER_BUILD_WRAPPED_STAGES, dockerImageForBuild, dockerArguments,
                        [
                                stage(CHECKOUT, false) {
                                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                                },
                                stage(VERSION_UPDATE, isStaging ? StageStrategy.FAIL_WHEN_STAGE_ERROR : StageStrategy.SKIP_STAGE) {
                                    versionUpdateStageBody(script,
                                            repoTag,
                                            gradleBuildFile,
                                            appVersionNameGradleVar,
                                            appVersionCodeGradleVar)
                                },
                                stage(BUILD) {
                                    BackendPipelineHelper.buildStageBodyBackend(script, buildGradleTask)
                                },
                                stage(UNIT_TEST) {
                                    BackendPipelineHelper.runUnitTests(script, unitTestGradleTask, unitTestResultPathXml, unitTestResultDirHtml)
                                },
                                stage(VERSION_PUSH, isStaging ? StageStrategy.UNSTABLE_WHEN_STAGE_ERROR : StageStrategy.SKIP_STAGE) {
                                    versionPushStageBody(script,
                                            repoTag,
                                            branchesPatternsForAutoChangeVersion,
                                            repoUrl,
                                            repoCredentialsId,
                                            prepareChangeVersionCommitMessage(
                                                    script,
                                                    gradleBuildFile,
                                                    appVersionNameGradleVar,
                                                    appVersionCodeGradleVar,
                                            ))
                                },
                                stage(DOCKER_BUILD_PUBLISH_IMAGE) {
                                    List<String> tags = new ArrayList<String>()
                                    String fullCommitHash = RepositoryUtil.getCurrentCommitHash(script)
                                    tags.add(repoTag)
                                    if (isStaging) {
                                        tags.add("dev-${fullCommitHash.reverse().take(8).reverse()}")
                                        tags.add("dev")
                                        def gradleVersionNumber = BackendUtil.getGradleVariableKtStyle(script, gradleBuildFile, appVersionCodeGradleVar)
                                        tags.add("$repoTag.$gradleVersionNumber")
                                    } else {
                                        tags.add("latest")
                                    }
                                    DockerHelper.buildDockerImageAndPush(script, registryPathAndProjectId, registryUrl, pathToDockerfile, tags)
                                }
                        ])
        ]
        finalizeBody = { finalizeStageBody(this) }
    }
    def static prepareChangeVersionCommitMessage(Object script,
                                                 String gradleConfigFile,
                                                 String appVersionNameGradleVar,
                                                 String appVersionCodeGradleVar){
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                BackendUtil.getGradleVariableKtStyle(script, gradleConfigFile, appVersionNameGradleVar))
        def versionCode = BackendUtil.getGradleVariableKtStyle(script, gradleConfigFile, appVersionCodeGradleVar)
        return "Change version to $versionName ($versionCode) $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }
    def static versionUpdateStageBody(Object script,
                                      String repoTag,
                                      String gradleConfigFile,
                                      String appVersionNameGradleVar,
                                      String appVersionCodeGradleVar) {
        BackendUtil.changeGradleVariableKtStyle(script, gradleConfigFile, appVersionNameGradleVar, "\"$repoTag\"")
        def codeStr = BackendUtil.getGradleVariableKtStyle(script, gradleConfigFile, appVersionCodeGradleVar)
        def newCodeStr = String.valueOf(Integer.valueOf(codeStr) + 1)
        BackendUtil.changeGradleVariableKtStyle(script, gradleConfigFile, appVersionCodeGradleVar, newCodeStr)

    }

}
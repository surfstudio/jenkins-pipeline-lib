package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.backend.DockerUtil
import ru.surfstudio.ci.utils.buildsystems.GradleUtil

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

    public gradleFileWithVersion = "build.gradle.kts"
    public appVersionNameGradleVar = "appVersionName"
    public appVersionCodeGradleVar = "appVersionCode"

    private isStaging = false

    public def productionDockerTags = ["latest"]
    public def stagingDockerTags = ["dev"]

    public def stagingTags = ["staging","snapshot","dev"]


    TagPipelineBackend(Object script) {
        super(script)
    }

    @Override
    def init() {
        node = NodeProvider.getBackendNode()

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = {
            initBody(this)
            CommonUtil.extractValueFromEnvOrParamsAndRun(script, REPO_TAG_PARAMETER) {
                tag -> isStaging = stagingTags.any{tag.toLowerCase().contains(it)}
            }
        }
        propertiesProvider = { properties(this) }



        stages = [
                docker(DOCKER_BUILD_WRAPPED_STAGES, dockerImageForBuild, dockerArguments,
                        [
                                stage(CHECKOUT, false) {
                                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                                },
                                stage(VERSION_UPDATE, isStaging ? StageStrategy.FAIL_WHEN_STAGE_ERROR : StageStrategy.SKIP_STAGE) {
                                    versionUpdateStageBody(script,
                                            repoTag,
                                            gradleFileWithVersion,
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
                                                    gradleFileWithVersion,
                                                    appVersionNameGradleVar,
                                                    appVersionCodeGradleVar,
                                            ))
                                }

                        ]) + [
                        stage(DOCKER_BUILD_PUBLISH_IMAGE) {
                            List<String> tags = new ArrayList<String>()
                            tags.add(repoTag)
                            if (isStaging) {
                                tags.addAll(stagingDockerTags)
                            } else {
                                tags.addAll(productionDockerTags)
                            }
                            DockerUtil.buildDockerImageAndPushIntoGoogleRegistry(script, registryPathAndProjectId, registryUrl, pathToDockerfile, tags)
                        }
                        ]
        ]
        finalizeBody = { finalizeStageBody(this) }
    }
    def static prepareChangeVersionCommitMessage(Object script,
                                                 String gradleConfigFile,
                                                 String appVersionNameGradleVar,
                                                 String appVersionCodeGradleVar){
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                GradleUtil.getGradleVariableKtStyle(script, gradleConfigFile, appVersionNameGradleVar))
        def versionCode = GradleUtil.getGradleVariableKtStyle(script, gradleConfigFile, appVersionCodeGradleVar)
        return "Change version to $versionName ($versionCode) $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }
    def static versionUpdateStageBody(Object script,
                                      String repoTag,
                                      String gradleConfigFile,
                                      String appVersionNameGradleVar,
                                      String appVersionCodeGradleVar) {
        GradleUtil.changeGradleVariableKtStyle(script, gradleConfigFile, appVersionNameGradleVar, "\"$repoTag\"")
        def codeStr = GradleUtil.getGradleVariableKtStyle(script, gradleConfigFile, appVersionCodeGradleVar)
        def newCodeStr = String.valueOf(Integer.valueOf(codeStr) + 1)
        GradleUtil.changeGradleVariableKtStyle(script, gradleConfigFile, appVersionCodeGradleVar, newCodeStr)

    }

}
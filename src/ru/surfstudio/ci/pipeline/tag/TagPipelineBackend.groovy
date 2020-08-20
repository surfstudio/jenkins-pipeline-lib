package ru.surfstudio.ci.pipeline.tag

import ru.surfstudio.ci.CommonUtil
import ru.surfstudio.ci.JarvisUtil
import ru.surfstudio.ci.NodeProvider
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.Result
import ru.surfstudio.ci.pipeline.helper.BackendPipelineHelper
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.DockerUtil
import ru.surfstudio.ci.utils.buildsystems.GradleUtil

class TagPipelineBackend extends TagPipeline {

    //Additional stages
    public static CHECK_TAG = "Check tag"
    public static APPLY_DEPLOY_COMMAND_TAG = "Apply deploy command tag"
    public static CHECK_PRODUCTION_DEPLOY_STARTED_BY_HAND = "Check production deploy not started by hand"
    public static BUILD_PUBLISH_DOCKER_IMAGES = "Build and publish docker images"
    public static DOCKER_BUILD_WRAPPER = "Inside docker"
    public static DEPLOY = "Deploy"

    // ===== Tags & versions =====
    public deployCommandTagRegexp = /^deploy-.+$/  // "deploy-prod", "deploy-dev"
    // Examples: "0.2-dev-3", "2.0-prod", "1.3.5-staging-3-featureFoo"
    public versionRegexp = /^\d{1,4}\.\d{1,4}(\.\d{1,4})?-[A-Za-z]+(-[0-9]+(-[A-Za-z0-9]+)?)?$/
    // Full version / Tag is: <mainVersion>-<deployType>-<additionalVersionCounter>-<versionLabel>
    // <additionalVersionCounter> - only for not production
    // <versionLabel> - optional and can exist if additionalVersionCounter exist
    public fullVersion = "<unspecified>"                // equals $repoTag if $repoTag is not deploy command
    public deployType                   // "dev", "prod", etc.
    public mainVersion                // X.Y.Z or X.Y
    public additionalVersionCounter  // number
    public versionLabel               // some text

    public productionDeployTypes = ["prod"]
    public qaDeployTypes = ["staging"] // сборка которая идет на тестирование
    //version always last part of branch name
    public branchWithVersionRegexps = [/^[a-zA-Z0-9]+\/\d{1,4}\.\d{1,4}(\.\d{1,4})?$/]
    //how to divide version from branch name
    public branchWithVersionDelimiters = "/"

    public versionUpdatedInSourceCode = false

    // ===== Gradle =====
    public gradleFileWithVersion = "build.gradle.kts"
    public appVersionNameGradleVar = "version"
    public buildGradleTask = "clean assemble"
    public unitTestGradleTask = "test"
    public unitTestResultPathXml = "build/test-results/test/*.xml"
    public unitTestResultDirHtml = "build/reports/tests/test"

    // ===== Docker ======
    // Full image path is: <dockerRegistryUrl>/<dockerRepository>/<imageName>:<fullVersion>
    public dockerRegistryUrl = "eu.gcr.io" //google registry by default
    //google registry by default, prefix "gcr:" for https://plugins.jenkins.io/google-container-registry-auth/
    public dockerRegistryCredentialsId = "gcr:google-container-registry-service-account"

    public dockerRepository = "<unspecified>"
    public dockerFiles = [:] // map - [imageName:dockerfilePath] e.g ['foo':'./foo/Dockerfile]
    public dockerImageAdditionalTags = ["latest"]

    public dockerImageForBuild = "gradle:6.0.1-jdk11"
    public dockerImageForBuildArguments = null


    TagPipelineBackend(Object script) {
        super(script)
        //override values from parent class
        tagRegexp = /.*/ //any, check tag in separate stage
        branchesPatternsForAutoChangeVersion = [/.*/]  //any
    }

    @Override
    def init() {
        node = NodeProvider.getBackendNode()

        preExecuteStageBody = { stage -> preExecuteStageBodyTag(script, stage, repoUrl) }
        postExecuteStageBody = { stage -> postExecuteStageBodyTag(script, stage, repoUrl) }

        initializeBody = {
            initBody(this)
        }

        propertiesProvider = { properties(this) }

        stages = [
                stage(CHECK_TAG) {
                    checkTagStageBody(this)
                },
                stage(CHECKOUT, false) {
                    checkoutStageBody(script, repoUrl, repoTag, repoCredentialsId)
                },
                stage(APPLY_DEPLOY_COMMAND_TAG, StageStrategy.SKIP_STAGE) {
                    applyDeployCommandTagStageBody(this)
                },
                stage(VERSION_UPDATE) {
                    versionUpdatedInSourceCode = versionUpdateStageBodyBackend(script,
                            fullVersion,
                            gradleFileWithVersion,
                            appVersionNameGradleVar)
                },
                stage(VERSION_PUSH, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    versionPushStageBody(script,
                            repoTag,
                            branchesPatternsForAutoChangeVersion,
                            repoUrl,
                            repoCredentialsId,
                            prepareChangeVersionCommitMessage(
                                    script,
                                    gradleFileWithVersion,
                                    appVersionNameGradleVar
                            ),
                            versionUpdatedInSourceCode)
                },
                stage(CHECK_PRODUCTION_DEPLOY_STARTED_BY_HAND) {
                    checkReleaseDeployStartedByHandStageBody(script,
                            productionDeployTypes.contains(deployType),
                            fullVersion)
                },
                docker(DOCKER_BUILD_WRAPPER, dockerImageForBuild, dockerImageForBuildArguments,
                        [
                                stage(BUILD) {
                                    BackendPipelineHelper.buildStageBodyBackend(script, buildGradleTask)
                                },
                                stage(UNIT_TEST) {
                                    BackendPipelineHelper.runUnitTests(script, unitTestGradleTask, unitTestResultPathXml, unitTestResultDirHtml)
                                }

                        ]),
                stage(BUILD_PUBLISH_DOCKER_IMAGES) {
                    dockerBuildPublishStageBody(script,
                            dockerRegistryUrl,
                            dockerRegistryCredentialsId,
                            dockerRepository,
                            dockerFiles,
                            fullVersion,
                            dockerImageAdditionalTags)
                },
                stage(DEPLOY, StageStrategy.UNSTABLE_WHEN_STAGE_ERROR) {
                    script.echo "This stage empty by default, please configure it in your jenkinsfile"
                    script.error("Empty stage")
                }
        ]
        finalizeBody = { finalizeStageBodyBackend(this) }
    }

    // =============================================== 	↓↓↓ EXECUTION LOGIC ↓↓↓ =================================================

    def static prepareChangeVersionCommitMessage(Object script,
                                                 String gradleConfigFile,
                                                 String appVersionNameGradleVar) {
        def versionName = CommonUtil.removeQuotesFromTheEnds(
                GradleUtil.getGradleVariable(script, gradleConfigFile, appVersionNameGradleVar))
        return "Change version to $versionName $RepositoryUtil.SKIP_CI_LABEL1 $RepositoryUtil.VERSION_LABEL1"

    }

    def static versionUpdateStageBodyBackend(Object script,
                                             String version,
                                             String gradleFileWithVersion,
                                             String appVersionNameGradleVar) {
        if (GradleUtil.getGradleVariable(script, gradleFileWithVersion, appVersionNameGradleVar) == version) {
            return false
        } else {
            GradleUtil.changeGradleVariable(script, gradleFileWithVersion, appVersionNameGradleVar, "\"$version\"")
            return true
        }

    }

    def static dockerBuildPublishStageBody(Object script,
                                           String registryUrl,
                                           String registryCredentialsId,
                                           String repository,
                                           Map<String, String> dockerFiles, //map[imageName:dockerFilePath]
                                           String version,
                                           Collection<String> additionalTags) {

        // Full image path is: <registryUrl>/<repository>/<imageName>:<version|additionalTag>

        if (dockerFiles.isEmpty()) {
            script.error("No dockerfiles specified")
        }
        DockerUtil.withRegistry(script, registryUrl, registryCredentialsId) {
            def imagePrefix = "$registryUrl/$repository"
            dockerFiles.each { file ->
                def imageName = "$imagePrefix/$file.key:$version"
                def splittedPath = file.value.split('/').toList()
                def dockerFileDir = splittedPath.subList(0, splittedPath.size() - 1).join('/')
                def image = script.docker.build(imageName, "-f $file.value $dockerFileDir")
                image.push()
                for (String imageTag : additionalTags) {
                    image.push(imageTag)
                }
            }
        }
    }

    def static checkTagStageBody(TagPipelineBackend ctx) {
        def script = ctx.script
        //todo check prod tag without counter
        if (ctx.repoTag ==~ ctx.deployCommandTagRegexp) {
            ctx.getStage(APPLY_DEPLOY_COMMAND_TAG).strategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
        } else if (ctx.repoTag ==~ ctx.versionRegexp) {
            fillVersionParts(ctx, ctx.repoTag)
        } else {
            script.error("Tag must match deployCommandTagRegexp: $ctx.deployCommandTagRegexp or versionRegexp: $ctx.versionRegexp")
        }
    }

    def static applyDeployCommandTagStageBody(TagPipelineBackend ctx) {
        def script = ctx.script
        def prevVersion = GradleUtil.getGradleVariable(script, ctx.gradleFileWithVersion, ctx.appVersionNameGradleVar)
        prevVersion = prevVersion.substring(1, prevVersion.length()-1) //remove quotes
        fillVersionParts(ctx, prevVersion)
        ctx.deployType = ctx.repoTag.replace("deploy-", "")
        def isDeployToProduction = ctx.productionDeployTypes.contains(ctx.deployType)

        //try find new main version in branch name
        def branches = RepositoryUtil.getBranchesForCurrentCommit(script)
        def branchWithVersion = null
        for (branchRegexp in ctx.branchWithVersionRegexps) {
            for (branch in branches) {
                if (branch ==~ branchRegexp) {
                    branchWithVersion = branch
                    break
                }
            }
            if (branchWithVersion) {
                break
            }
        }
        if (branchWithVersion != null) {
            def parts = branchWithVersion.split(ctx.branchWithVersionDelimiters)
            ctx.mainVersion = parts[parts.length - 1]
        }

        //calculate version parts depends on deployType
        if (isDeployToProduction) {
            ctx.additionalVersionCounter = null
            ctx.versionLabel = null
        } else {
            if (ctx.additionalVersionCounter == null) {
                ctx.additionalVersionCounter = 0
            } else {
                ctx.additionalVersionCounter++
            }
        }

        //collect parts into full version
        assembleFullVersionFromParts(ctx)

        //check new version tag is not exist
        def tags = script.sh(returnStdout: true, script: "git tag").trim().split("\n").toList()
        if (isDeployToProduction && tags.contains(ctx.fullVersion)) {
            script.error("Version $ctx.fullVersion already deployed, pleace change version and start again")
        }

        while (tags.contains(ctx.fullVersion)) {
            ctx.additionalVersionCounter++
            assembleFullVersionFromParts(ctx)
        }

        //delete tag-command
        script.sh "git tag -d $ctx.repoTag"
        script.sh "git push --delete origin $ctx.repoTag"
    }

    def static checkReleaseDeployStartedByHandStageBody(Object script,
                                                        boolean isReleaseDeploy,
                                                        String fullVersion) {
        if (isReleaseDeploy && !CommonUtil.isJobStartedByUser(script)) {
            throw new InterruptedException("Deploy to production must be started by hands, please, start job: ${CommonUtil.getClassicJobLink(script)} with tag parameter: $fullVersion")
        }
    }

    def static fillVersionParts(TagPipelineBackend ctx, String version) {
        def script = ctx.script
        if (!(version ==~ ctx.versionRegexp)) {
            script.error("app version must matches to regexp: $ctx.versionRegexp")
        }
        ctx.fullVersion = version
        def splittedVersion = version.split("-")
        ctx.mainVersion = splittedVersion[0]
        ctx.deployType = splittedVersion[1]
        if (splittedVersion.size() > 2) {
            ctx.additionalVersionCounter = Integer.parseInt(splittedVersion[2])
        }
        if (splittedVersion.size() > 3) {
            ctx.versionLabel = splittedVersion[3]
        }
    }


    def static assembleFullVersionFromParts(TagPipelineBackend ctx) {
        def fullVersion = "$ctx.mainVersion-$ctx.deployType"
        if (ctx.additionalVersionCounter != null) fullVersion += "-$ctx.additionalVersionCounter"
        if (ctx.versionLabel != null) fullVersion += "-$ctx.versionLabel"
        ctx.fullVersion = fullVersion
    }

    def static finalizeStageBodyBackend(TagPipelineBackend ctx) {
        def script = ctx.script
        if (ctx.getStage(CHECKOUT).result != Result.ABORTED) { //do not handle builds skipped via [skip ci] label
            if (ctx.qaDeployTypes.contains(ctx.deployType)) {
                ctx.repoTag = ctx.fullVersion
                //todo хак чтобы не переписывать метод createVersionAndNotify, нужно вообще пересмотреть как работать в этом месте с джарвисом
                JarvisUtil.createVersionAndNotify(ctx)
            } else {
                def message = null
                def result = ctx.jobResult
                if (ctx.getStage(CHECK_PRODUCTION_DEPLOY_STARTED_BY_HAND).result == Result.ABORTED) {
                    message = "Развертывание в продакшн для безопасности должно быть запущено вручную, пожалуйста, запустите ${CommonUtil.toSlackLink(CommonUtil.getClassicJobLink(script) + "build?delay=0sec", "Jenkins Job")} с праметром tag: $ctx.fullVersion"
                } else if (ctx.jobResult != Result.SUCCESS && ctx.jobResult != Result.ABORTED && ctx.jobResult != Result.NOT_BUILT) {
                    def unsuccessReasons = CommonUtil.unsuccessReasonsToString(ctx.stages)
                    message = "Развертывание по тегу ${ctx.repoTag} завершилось с результатом ${ctx.jobResult} из-за этапов: ${unsuccessReasons}; ${CommonUtil.getBuildUrlSlackLink(ctx.script)}; создание версии в джире и перенос задач не предусмотрены для $ctx.deployType развертывания"
                } else if (ctx.jobResult == Result.SUCCESS) {
                    message = "Развертывание c версией ${ctx.fullVersion} произведено успешно, создание версии в джире и перенос задач не предусмотрены для $ctx.deployType развертывания: "
                }
                JarvisUtil.sendMessageToGroup(ctx.script, message, ctx.repoUrl, "github", result)
            }
        }
    }


}
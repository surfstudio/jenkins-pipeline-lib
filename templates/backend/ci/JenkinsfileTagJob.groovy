@Library('surf-lib@version-4.0.0-SNAPSHOT')
import ru.surfstudio.ci.RepositoryUtil
import ru.surfstudio.ci.pipeline.tag.TagPipelineBackend
import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.utils.YamlUtil

def script = this

def pipeline = new TagPipelineBackend(script)
pipeline.init()

// TODO донастроить пайплайн под конкретный проект

pipeline.dockerFiles = ["foo":"./foo/Dockerfile", "bar":"./Dockerfile"] // [imageName:dockerFilePath]
pipeline.getStage(pipeline.BUILD_PUBLISH_DOCKER_IMAGES).beforeBody {
    //перед выполнением стейджа со сборкой образов устанавливаем переменную dockerRepository, зависящую от места деплоя
    pipeline.dockerRepository = "foo/bar/$pipeline.deployType"
}


def k8sRepo = "<undefined>" // repo with k8s configs
def k8sBranch = "<undefined>" // branch with k8s configs in repo $k8sRepo
pipeline.getStage(pipeline.DEPLOY).strategy = StageStrategy.FAIL_WHEN_STAGE_ERROR
pipeline.getStage(pipeline.DEPLOY).body = {
    dir("tmp-k8s"){
        script.git url: k8sRepo, credentialsId: pipeline.repoCredentialsId, branch: k8sBranch
        def fileToChange = "manifests/overlays/$pipeline.deployType/kustomization.yaml"
        YamlUtil.changeYamlVariable(script, fileToChange, "newTag", pipeline.fullVersion)
        script.sh "git commit -a -m \"New Docker tag: $pipeline.fullVersion for deploy: $pipeline.deployType\""
        RepositoryUtil.push(script, repoUrl, repoCredentialsId)
    }
}

pipeline.run()
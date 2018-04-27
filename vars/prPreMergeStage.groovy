
import ru.surfstudio.ci.PrPipeline

def static call(PrPipeline ctx) {
    ctx.origin.sh 'git config --global user.name "Jenkins"'
    ctx.origin.sh 'git config --global user.email "jenkins@surfstudio.ru"'
    ctx.origin.checkout changelog: true, poll: true, scm:
            [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${ctx.sourceBranch}"]],
                    doGenerateSubmoduleConfigurations: false,
                    userRemoteConfigs                : ctx.origin.scm.userRemoteConfigs,
                    extensions                       : [
                            [
                                    $class : 'PreBuildMerge',
                                    options: [
                                            mergeStrategy  : 'MergeCommand.Strategy',
                                            fastForwardMode: 'NO_FF',
                                            mergeRemote    : 'origin',
                                            mergeTarget    : "${ctx.destinationBranch}"
                                    ]
                            ]
                    ]
            ]
    ctx.origin.echo 'PreMerge Success'
}

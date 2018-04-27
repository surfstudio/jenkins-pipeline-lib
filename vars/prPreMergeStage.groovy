
import ru.surfstudio.ci.PrContext

def static call(PrContext ctx) {
    sh 'git config --global user.name "Jenkins"'
    sh 'git config --global user.email "jenkins@surfstudio.ru"'
    checkout changelog: true, poll: true, scm:
            [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${ctx.sourceBranch}"]],
                    doGenerateSubmoduleConfigurations: false,
                    userRemoteConfigs                : scm.userRemoteConfigs,
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
    echo 'PreMerge Success'
}

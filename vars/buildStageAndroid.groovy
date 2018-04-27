import ru.surfstudio.ci.Pipeline


def static call(Pipeline ctx, String buildGradleTask) {
    ctx.origin.sh "./gradlew clean ${buildGradleTask}"
    ctx.origin.step([$class: 'ArtifactArchiver', artifacts: '**/*.apk'])
}
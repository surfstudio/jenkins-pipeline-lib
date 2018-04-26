package ru.surfstudio.ci

class Test {
    static void testPipline(script) {
        script.node("android.node"){
            script.stage("test stage") {
                script.echo "Home ${script.env.HOME}"
            }
        }
    }
}

package ru.surfstudio.ci

class Test implements Serializable {
    private script;

    Test(script) {
        this.script = script
    }

    void testPipline() {
        script.node("android.node"){
            script.stage("test stage") {
                script.echo "Home ${script.env.HOME}"
            }
        }
    }
}

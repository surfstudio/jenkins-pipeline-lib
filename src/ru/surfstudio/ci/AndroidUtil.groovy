package ru.surfstudio.ci

class AndroidUtil {

    def static onEmulator(Object script, String avdName, Closure body) {
        script.timeout(time: 2*60*60, unit: 'SECONDS') { //2 hours
            def ADB = "${script.env.ANDROID_HOME}/platform-tools/adb"
            def EMULATOR = "${script.env.ANDROID_HOME}/tools/emulator"
            script.sh "$ADB devices"
            script.sh "$EMULATOR -list-avds"
            script.lock(avdName) { //блокируем эмулятор
                script.sh "$EMULATOR -avd avd-main -no-window -skin 1440x2560 &"
                script.timeout(time: 120, unit: 'SECONDS') { //2мин ждем запуск девайса
                    script.sh "$ADB wait-for-device"
                }
                //нажимаем кнопку домой
                script.sh "$ADB shell input keyevent 3 &"
                body()
            }
        }
    }
}

package ru.surfstudio.ci.pipeline.pr.utils

/**
 * Класс, содержащий параметры конфигурации запуска инструментальных тестов
 */
class AndroidTestConfig {

    // buildType, для которого будут выполняться инструментальные тесты
    String testBuildType = "qa"

    // имя AVD
    String avdName = "avd-androidTest"

    // имя девайса для запуска AVD
    String deviceName = "Nexus 5X"

    // SDK ID для запуска эмулятора
    String sdkId = "system-images;android-28;google_apis;x86"

    // размер объема sdcard эмулятора
    String sdcardSize = "3072M"

    // разрешение эмулятора
    String skinSize = "1440x2560"

    // имя skin для эмулятора
    String skinName = "nexus_5x"

    // флаг, показывающий, будет ли запущен ранее созданный эмулятор
    Boolean reuse = true

    // флаг, показывающий, нужно ли сохранять состояние эмулятора при его закрытии,
    // либо нужно удалить эмулятор после завершения инструментальных тестов
    Boolean stay = true
}

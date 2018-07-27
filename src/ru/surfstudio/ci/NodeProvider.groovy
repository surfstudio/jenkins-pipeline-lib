package ru.surfstudio.ci

class NodeProvider {
    def static getAndroidNode() {
        return "android" //"android" - метка, поэтому будет использоваться один из доступных компьютеров с этой метокй
    }

    def static getiOSNode() {
        return "ios"
    }

    def static getAutoAbortNode() {
        return "auto_abort" // нод с этой меткой должен быть всегда доступен, поскольку на нем происходит удаление повторяющихся билдов
    }

}

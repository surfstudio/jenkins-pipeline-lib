package ru.surfstudio.ci

class NodeProvider {
    def static getAndroidNode() {
        return "android" //"android" - метка, поэтому будет использоваться один из доступных компьютеров с этой метокй
    }

    def static getiOSNode() {
        return "ios"
    }
}

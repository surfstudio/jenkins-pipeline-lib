#!/usr/bin/groovy
package ru.surfstudio.ci

abstract class BaseContext implements Serializable {

    public origin //Jenkins Pipeline Script context
    public stageResults = [:]
    public jobResult = Result.SUCCESS

    BaseContext(origin) {
        this.origin = origin
    }
}

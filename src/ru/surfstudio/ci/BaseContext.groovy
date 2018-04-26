#!/usr/bin/groovy
package ru.surfstudio.ci

abstract class BaseContext {

    BaseContext(script) {
        this.script = script
    }

    public script
    public stageResults = [:]
    public jobResult = Result.SUCCESS
}

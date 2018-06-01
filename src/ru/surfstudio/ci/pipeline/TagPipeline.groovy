package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy

abstract class TagPipeline extends Pipeline {

    //stage names
    public static final String INIT = 'Init'
    public static final String CHECKOUT = 'Checkout'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'
    public static final String BETA_UPLOAD = 'Beta Upload'

    //scm
    public repoTag = ""

    //ios
    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    TagPipeline(Object script) {
        super(script)
    }
}
package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.stage.body.PrStages

abstract class PrPipeline extends Pipeline {

    //stage names
    public static final String INIT = 'Init'
    public static final String PRE_MERGE = 'PreMerge'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""

    //ios
    public iOSKeychainCredenialId = "add420b4-78fc-4db0-95e9-eeb0eac780f6"
    public iOSCertfileCredentialId = "IvanSmetanin_iOS_Dev_CertKey"

    PrPipeline(Object script) {
        super(script)
    }
}
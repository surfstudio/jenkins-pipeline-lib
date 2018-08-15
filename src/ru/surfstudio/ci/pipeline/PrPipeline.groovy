package ru.surfstudio.ci.pipeline

import ru.surfstudio.ci.stage.StageStrategy
import ru.surfstudio.ci.stage.body.CommonAndroidStages
import ru.surfstudio.ci.stage.body.PrStages

abstract class PrPipeline extends AutoAbortedPipeline {

    //stage names
    public static final String PRE_MERGE = 'PreMerge'
    public static final String BUILD = 'Build'
    public static final String UNIT_TEST = 'Unit Test'
    public static final String INSTRUMENTATION_TEST = 'Instrumentation Test'
    public static final String STATIC_CODE_ANALYSIS = 'Static Code Analysis'

    //required configuration
    public repoFullName = ""

    //scm
    public sourceBranch = ""
    public destinationBranch = ""
    public authorUsername = ""
    public boolean targetBranchChanged = false


    //other config
    public stagesForTargetBranchChangedMode = [PRE_MERGE]

    PrPipeline(Object script) {
        super(script)
    }

    @Override
    String getBuildIdentifier() {
        if(targetBranchChanged) {
            return "$sourceBranch: target branch changed"
        } else {
            return sourceBranch
        }
    }
}
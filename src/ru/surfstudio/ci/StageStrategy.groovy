package ru.surfstudio.ci

public class StageStrategy implements Serializable {
    public static final String SKIP_STAGE = "SKIP_STAGE"
    public static final String FAIL_WHEN_STAGE_ERROR = "FAIL_WHEN_STAGE_ERROR"
    public static final String UNSTABLE_WHEN_STAGE_ERROR = "UNSTABLE_WHEN_STAGE_ERROR"
    public static final String SUCCESS_WHEN_STAGE_ERROR = "SUCCESS_WHEN_STAGE_ERROR"
}
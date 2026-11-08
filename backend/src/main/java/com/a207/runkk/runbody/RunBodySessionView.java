package com.a207.runkk.runbody;

/**
 * 런바디 화면에 줄 세션 상태와 결과(설계 05-19 2.2, FR-266).
 *
 * @param recordId 기록 ID
 * @param status 작업 상태(DONE이 아니면 result는 null)
 * @param result ai 세션 결과 JSON(DONE일 때만)
 * @param failureReason FAILED일 때 거부 사유(BIRTH_YEAR_MISSING 등), 없으면 null(MR !91 e2e 1)
 */
public record RunBodySessionView(
        String recordId, RunBodyStatus status, String result, String failureReason) {

    /** 거부 사유가 없는 상태. */
    public RunBodySessionView(String recordId, RunBodyStatus status, String result) {
        this(recordId, status, result, null);
    }
}

package com.a207.runkk.runbody;

/**
 * ai 세션 결과 조회 결과. 본문은 ai GET /sessions/{id} 200 본문 JSON 그대로다(D-442, 앱 계약 1.1).
 *
 * @param status DONE이면 body가 있다. ANALYZING·FAILED면 body는 null
 * @param userId ai 본문의 userId(DONE일 때만)
 * @param body ai 본문 JSON(DONE일 때만)
 */
public record RunBodySession(RunBodyStatus status, String userId, String body) {

    public static RunBodySession done(String userId, String body) {
        return new RunBodySession(RunBodyStatus.DONE, userId, body);
    }

    public static RunBodySession notReady(RunBodyStatus status) {
        return new RunBodySession(status, null, null);
    }
}

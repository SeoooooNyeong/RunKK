package com.a207.runkk.runbody;

/**
 * ai 세션 결과 조회 결과. 본문은 ai GET /sessions/{id} 200 본문 JSON 그대로다(D-442, 앱 계약 1.1).
 *
 * @param status DONE이면 body가 있다. ANALYZING·FAILED면 body는 null
 * @param userId ai 본문의 userId(DONE일 때만)
 * @param body ai 본문 JSON(DONE일 때만)
 * @param score 본문 axes에서 셈한 러닝 점수(RunBodyScore.overall). 점수가 있는 축이 없거나 DONE이 아니면 null
 */
public record RunBodySession(RunBodyStatus status, String userId, String body, Integer score) {

    public static RunBodySession done(String userId, String body) {
        return done(userId, body, null);
    }

    public static RunBodySession done(String userId, String body, Integer score) {
        return new RunBodySession(RunBodyStatus.DONE, userId, body, score);
    }

    public static RunBodySession notReady(RunBodyStatus status) {
        return new RunBodySession(status, null, null, null);
    }
}

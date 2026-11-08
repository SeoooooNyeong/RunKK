package com.a207.runkk.runbody;

/**
 * ai 월간 집계. 본문은 ai GET /users/{id}/months/{ym} 200 본문 JSON 그대로다(D-442, 앱 계약 2.1).
 *
 * @param userId ai 본문의 userId
 * @param body ai 본문 JSON
 */
public record RunBodyMonth(String userId, String body) {}

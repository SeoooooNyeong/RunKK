package com.a207.runkk.crew;

/**
 * 가입 결정 결과(자료사전 가입결정결과). 소멸이면 사유(정원 초과, 다른 크루 가입)를 싣는다(FR-142).
 *
 * @param requestId 가입 신청 ID
 * @param status 처리 뒤 신청 상태
 * @param reason 소멸 사유, 없으면 null
 */
public record DecisionResult(long requestId, JoinRequestStatus status, String reason) {}

package com.a207.runkk.crew;

/**
 * 가입 신청 결과(자료사전 가입신청결과: 가입완료 | 승인대기 | 신청취소). 거부는 BusinessRuleException이다.
 *
 * @param status 결과
 * @param crewId 크루 ID
 * @param requestId 승인 대기·취소면 가입 신청 ID
 */
public record JoinResult(Status status, long crewId, Long requestId) {

    /** 결과 종류. */
    public enum Status {
        JOINED,
        PENDING,
        CANCELLED
    }
}

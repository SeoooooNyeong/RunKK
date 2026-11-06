package com.a207.runkk.member;

/**
 * 본인 배치고사 진행 상태(05-03 5.7.3.1, D-488). IN_PROGRESS = 배치 중(회원 티어 UNRANKED), COMPLETED = 배치고사로 확정,
 * EXEMPT = 배치고사 없이 이미 티어가 있던 회원(배포 전 확정, 브리프 규칙 12).
 */
public enum PlacementStatus {
    IN_PROGRESS,
    COMPLETED,
    EXEMPT
}

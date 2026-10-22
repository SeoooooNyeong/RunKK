package com.a207.runkk.member;

/** MemberQueryService.pushDecision 결과(SQ-14). 탈퇴 회원이면 버리고, 그 종류를 껐으면 알림함에만 저장한다. */
public enum PushDecision {
    WITHDRAWN,
    ALLOWED,
    MUTED
}

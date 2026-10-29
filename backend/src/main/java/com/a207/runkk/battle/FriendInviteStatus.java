package com.a207.runkk.battle;

import com.a207.runkk.common.ReasonCode;
import java.util.Optional;

/**
 * 친구 대결 초대 상태(설계 2.4.11, D-218). PENDING에서만 다른 상태로 가고 끝 상태에서는 되돌아가지 않는다. srs 6.4.2 이름 대응: 초대 대기 =
 * PENDING, 참가됨 = ACCEPTED, 초대 거절 = DECLINED, 초대 취소 = CANCELLED, 초대 만료 = EXPIRED.
 */
public enum FriendInviteStatus {
    PENDING(null),
    ACCEPTED(ReasonCode.INVITE_ALREADY_JOINED),
    DECLINED(ReasonCode.INVITE_CANCELLED),
    CANCELLED(ReasonCode.INVITE_CANCELLED),
    EXPIRED(ReasonCode.INVITE_EXPIRED);

    private final ReasonCode refusal;

    FriendInviteStatus(ReasonCode refusal) {
        this.refusal = refusal;
    }

    /** 이 상태의 초대에 참가·거절하면 받는 거부 사유. PENDING은 없다. */
    public Optional<ReasonCode> refusal() {
        return Optional.ofNullable(refusal);
    }

    /** 끝 상태인가(PENDING만 거짓). */
    public boolean isFinal() {
        return this != PENDING;
    }
}

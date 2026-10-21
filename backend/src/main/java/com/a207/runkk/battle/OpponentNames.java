package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.member.MemberSummary;
import java.util.Map;
import java.util.Optional;

/**
 * 대결 상대 표시명(이력·결과 화면 공통, FR-093, FR-101). 기록 대결은 대결에 남긴 상대 표시명을 쓰고(FR-172), 랜덤 대결은 상대 회원 표시명, 회원이
 * 없으면 "탈퇴한 사용자"다(FR-021).
 */
final class OpponentNames {

    private OpponentNames() {}

    static DisplayName of(Battle battle, long memberId, Map<Long, MemberSummary> names) {
        Optional<OpponentRef> record = battle.getOpponent();
        if (record.isPresent()) {
            return record.get().displayName();
        }
        return battle.rivalOf(memberId)
                .map(rival -> names.get(rival.getMemberId()))
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }
}

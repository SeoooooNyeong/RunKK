package com.a207.runkk.crew;

import java.util.Optional;

/**
 * 가입 신청 대상(자료사전 가입신청: 크루ID | 초대코드). 둘 중 하나만 있다.
 *
 * @param crewId 크루 ID로 신청할 때
 * @param inviteCode 초대 코드로 신청할 때
 */
public record JoinTarget(Long crewId, String inviteCode) {

    public JoinTarget {
        boolean byCrew = crewId != null;
        boolean byCode = inviteCode != null && !inviteCode.isBlank();
        if (byCrew == byCode) {
            throw new IllegalArgumentException("크루 ID와 초대 코드 중 하나만 준다");
        }
    }

    public static JoinTarget byCrew(Long crewId) {
        return new JoinTarget(crewId, null);
    }

    public static JoinTarget byCode(String inviteCode) {
        return new JoinTarget(null, inviteCode);
    }

    public Optional<String> code() {
        return Optional.ofNullable(inviteCode);
    }
}

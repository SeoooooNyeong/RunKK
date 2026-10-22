package com.a207.runkk.crew;

import java.time.LocalDateTime;

/**
 * 크루 설정·소속 변경 결과(자료사전 크루설정결과 등). 초대 코드 재발급이면 새 코드를 싣는다.
 *
 * @param crewId 크루 ID
 * @param inviteCode 새 초대 코드, 없으면 null
 * @param inviteCodeExpiresAt 새 코드 만료 일시, 없으면 null
 */
public record CrewResult(long crewId, String inviteCode, LocalDateTime inviteCodeExpiresAt) {

    static CrewResult of(long crewId) {
        return new CrewResult(crewId, null, null);
    }

    static CrewResult withCode(Crew crew) {
        return new CrewResult(
                crew.getId(), crew.inviteCode().code(), crew.inviteCode().expiresAt());
    }
}

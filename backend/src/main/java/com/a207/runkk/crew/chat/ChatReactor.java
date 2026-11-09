package com.a207.runkk.crew.chat;

import java.time.OffsetDateTime;

/**
 * 메시지에 반응한 사람 한 줄(피그마 F-09d, 사용자 결정 C7). 반응 한 건이 한 줄이라 두 종류를 단 회원은 두 줄이다.
 *
 * @param memberId 반응한 회원 ID(탈퇴 회원의 반응은 탈퇴 때 지워져 없다)
 * @param displayName 표시명
 * @param kind 반응 종류
 * @param reactedAt 반응한 일시(서버 시간대 오프셋)
 * @param me 요청자 본인
 */
public record ChatReactor(
        long memberId,
        String displayName,
        ReactionKind kind,
        OffsetDateTime reactedAt,
        boolean me) {}

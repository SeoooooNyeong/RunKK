package com.a207.runkk.operator;

import com.a207.runkk.member.SocialAccount;

/**
 * 운영자 화이트리스트 항목 하나(설계 6.1.12 OperatorId).
 *
 * @param account 소셜 제공자 계정
 */
public record OperatorId(SocialAccount account) {

    public OperatorId {
        if (account == null) {
            throw new IllegalArgumentException("운영자 계정이 비었다");
        }
    }

    /** 감사 기록·서비스 호출에 넘길 운영자 계정 문자열("KAKAO:12345"). */
    public String actorId() {
        return account.provider().name() + ":" + account.providerAccountId();
    }
}

package com.a207.runkk.battle;

import com.a207.runkk.member.Gender;
import java.util.random.RandomGenerator;

/**
 * 대결 상대 캐릭터 성별(2026-10-31 결정). 앱이 이 값으로 상대 캐릭터 그림을 고른다. 회원 상대(고스트 러너 기록 주인, 랜덤·친구 대결 상대)는 그 회원 성별,
 * 성별이 없거나 밝히지 않았거나 성별 공개가 아니면(MemberQueryService.publicGendersOf) 가상 상대처럼 남·여 중 무작위다. 셀럽·나와의 싸움은
 * 정하지 않는다(null). 매칭 때 한 번 정해 대결에 남기므로 다시 연결·결과 화면에서도 같은 그림이다.
 */
final class OpponentGenders {

    private OpponentGenders() {}

    /** 회원 성별이 남·여면 그대로, 아니면 무작위. */
    static Gender of(Gender memberGender, RandomGenerator random) {
        if (memberGender == Gender.MALE || memberGender == Gender.FEMALE) {
            return memberGender;
        }
        return random(random);
    }

    /**
     * 회원이 보는 상대 캐릭터 성별 이름(MALE, FEMALE), 정하지 않았으면 null. 응답·실시간 메시지는 member 형식을 싣지 않고 이 문자열을
     * 싣는다(13.1 규칙 7·web.battle 의존 범위).
     */
    static String nameFor(Battle battle, long memberId) {
        return battle.opponentGenderFor(memberId).map(Gender::name).orElse(null);
    }

    /** 남·여 중 하나(가상 상대). */
    static Gender random(RandomGenerator random) {
        return random.nextBoolean() ? Gender.FEMALE : Gender.MALE;
    }
}

package com.a207.runkk.battle;

import com.a207.runkk.member.Gender;
import com.a207.runkk.member.ShownGender;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 대결 상대 캐릭터 성별(2026-10-31 결정, D-336). 앱이 이 값으로 상대 캐릭터 그림을 고른다. 회원 상대(고스트 러너 기록 주인, 랜덤·친구 대결 상대)는 그
 * 회원의 표시 성별(MemberQueryService.shownGendersOf: 공개 남·여면 그 값, 아니면 회원 ID 고정 대체값)이고, 가상 상대만 남·여 중 무작위다.
 * 셀럽·나와의 싸움은 정하지 않는다(null). 매칭 때 한 번 정해 대결에 남기므로 다시 연결·결과 화면에서도 같은 그림이다.
 */
final class OpponentGenders {

    private OpponentGenders() {}

    /** 표시 성별 맵에서 회원 값. 맵에 없으면(조회 누락) 같은 규칙의 대체값이다. */
    static Gender shownOf(Map<Long, Gender> shown, long memberId) {
        Gender gender = shown.get(memberId);
        return gender == Gender.MALE || gender == Gender.FEMALE
                ? gender
                : ShownGender.fallbackOf(memberId);
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

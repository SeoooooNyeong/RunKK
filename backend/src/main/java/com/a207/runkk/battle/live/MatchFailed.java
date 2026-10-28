package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;

/**
 * 랜덤·고스트 대결을 만들지 못해 매칭 대기가 풀렸다는 알림(B3 리뷰: 대결 생성 실패 시 앱이 하염없이 기다림). 설계 5.7.2 메시지 6종 밖에 더한 7번째 대결
 * 메시지다. 앱은 대기 화면을 닫고 다시 요청하게 한다.
 *
 * @param code 사유 코드(MATCH_FAILED)
 * @param message 화면 문구
 */
public record MatchFailed(String code, String message) implements BattleMessage {

    @Override
    public String type() {
        return "MatchFailed";
    }
}

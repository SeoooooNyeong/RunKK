package com.a207.runkk.member;

import java.util.Optional;

/**
 * 탈퇴를 막을 사유가 있는지 묻는 포트(설계 6.1.2, srs.md 6.3 1.5). 구현은 battle.ActiveBattleGuard("진행 중인 대결 종료 필요"),
 * crew.CrewLeaderGuard("크루장 양도 필요"). member가 battle·crew를 모르게 받는 쪽이 정의한다(5.2).
 */
public interface WithdrawalGuard {

    /** 거부 사유. 없으면 비어 있음. */
    Optional<String> check(long memberId);
}

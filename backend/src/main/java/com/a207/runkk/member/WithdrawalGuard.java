package com.a207.runkk.member;

import com.a207.runkk.common.ReasonCode;
import java.util.Optional;

/**
 * 탈퇴를 막을 사유가 있는지 묻는 포트(설계 6.1.2, srs.md 6.3 1.5). 구현은 battle.ActiveBattleGuard(BATTLE_IN_PROGRESS
 * "진행 중인 대결 종료 필요"), crew.CrewLeaderGuard(LEADER_MUST_TRANSFER "크루장 양도 필요"). member가 battle·crew를
 * 모르게 받는 쪽이 정의한다(5.2). 사유는 사유 코드로 주고 WithdrawalService가 BusinessRuleException으로 던져 공통 오류 응답(409
 * code·message)이 된다(앱–서버 대조 B 단계).
 */
public interface WithdrawalGuard {

    /** 거부 사유 코드. 없으면 비어 있음. */
    Optional<ReasonCode> check(long memberId);
}

package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;

/**
 * 회원의 진행 중인 대결(배틀런 QA 7번). 앱이 재실행 때 읽어 "진행 중인 대결이 있어요"와 그만두기를 보인다. 고스트는 GHOST 그대로다.
 *
 * @param battleId 대결 ID
 * @param type 대결 종류
 * @param status 활성 상태(WAITING_START, IN_PROGRESS, COUNTDOWN)
 * @param distance 대결 거리
 * @param opponentName 상대 표시명(탈퇴면 "탈퇴한 사용자")
 * @param startAt 출발 시각, 시작 보고 전 기록 대결이면 null
 */
public record ActiveBattleView(
        long battleId,
        BattleType type,
        BattleStatus status,
        Distance distance,
        DisplayName opponentName,
        LocalDateTime startAt) {}

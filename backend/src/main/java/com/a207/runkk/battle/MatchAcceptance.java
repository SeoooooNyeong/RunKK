package com.a207.runkk.battle;

import com.a207.runkk.common.StandardDistance;

/**
 * 매칭 접수 결과(자료사전 매칭접수결과 = 대기등록 | 대기해제 | 거부 + 거부사유). 거부는 BusinessRuleException 사유 코드로 알린다.
 *
 * @param waiting 대기 중이면 true(대기등록), 대기를 풀었으면 false(대기해제)
 * @param distance 대기 거리, 대기해제면 null
 * @param defaultDistance 회원의 기본 대결 거리(마지막으로 요청한 거리, FR-080·FR-109). 홈 배틀런 버튼이 이 거리로 바로 요청한다
 * @param placementRound 배치고사로 만든 대결의 판 번호(1~3), 그 밖은 null(D-479)
 * @param battleId 배치고사로 만든 대결 ID, 그 밖은 null
 */
public record MatchAcceptance(
        boolean waiting,
        StandardDistance distance,
        StandardDistance defaultDistance,
        Integer placementRound,
        Long battleId) {

    /** 배치고사가 아닌 접수(placementRound·battleId 없음). */
    public MatchAcceptance(
            boolean waiting, StandardDistance distance, StandardDistance defaultDistance) {
        this(waiting, distance, defaultDistance, null, null);
    }

    static MatchAcceptance waitingFor(StandardDistance distance) {
        return new MatchAcceptance(true, distance, distance);
    }

    static MatchAcceptance released(StandardDistance defaultDistance) {
        return new MatchAcceptance(false, null, defaultDistance);
    }

    /** 배치고사 대결을 바로 만들었다(D-479): 대기 아님, 거리·기본 거리 = 고른 거리, 판 번호, 대결 ID. */
    public static MatchAcceptance placement(StandardDistance d, int round, long battleId) {
        return new MatchAcceptance(false, d, d, round, battleId);
    }
}

package com.a207.runkk.analysis;

import java.time.LocalDateTime;

/**
 * 랭킹 다시 계산 한 번의 결과(RankingService.recompute, 운영자 관리 API 응답). 개인·크루 건수는 주간·월간이 같은 대상이라 하나씩이다.
 *
 * @param computedAt 집계 시각(한국 시간, 조회 응답의 집계 기준 시각과 같다)
 * @param personalCount 개인 랭킹에 든 회원 수(나만 보기 제외)
 * @param crewCount 크루 랭킹에 든 크루 수
 * @param failedSnapshots 저장하지 못한 스냅숏 수(0~4). 실패한 종류·기간은 이전 스냅숏이 그대로 남는다
 */
public record RankingRecomputeResult(
        LocalDateTime computedAt, int personalCount, int crewCount, int failedSnapshots) {}

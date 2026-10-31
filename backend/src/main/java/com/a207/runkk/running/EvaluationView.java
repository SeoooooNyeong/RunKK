package com.a207.runkk.running;

import com.a207.runkk.common.Tier;

/**
 * 한 러닝이 일으킨 티어 평가 표시(v0.14 D-291·D-294, RunQueryService.evaluationOf). 기록 상세(web.record)와 대결
 * 결과(web.battle, 12.2 W5)가 응답에 붙인다. web.battle이 member를 읽지 않게 종류는 이름 문자열이다.
 *
 * @param kind FIRST_PLACEMENT 또는 REEVALUATION
 * @param tier 평가 뒤 티어
 * @param validRuns 평가를 일으킨 유효 러닝 순번(첫 배치 3, 재평가 10의 배수)
 * @param runimalType 그 판정에서 확정된 Runimal 유형 이름(첫 배치는 늘 null, 확정되지 않았으면 null)
 */
public record EvaluationView(String kind, Tier tier, int validRuns, String runimalType) {}

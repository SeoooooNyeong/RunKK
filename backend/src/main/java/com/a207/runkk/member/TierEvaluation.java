package com.a207.runkk.member;

/**
 * 유효 러닝 한 회가 일으킨 티어 평가(v0.14 D-291·D-294). 평가 시점 규칙의 주인인 TierProgress가 정한다.
 *
 * @param kind 첫 배치(유효 3회째) 또는 재평가(10의 배수 회차)
 * @param validRuns 평가를 일으킨 유효 러닝 순번(첫 배치 3, 재평가 10의 배수)
 */
public record TierEvaluation(EvaluationKind kind, int validRuns) {}

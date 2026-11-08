package com.a207.runkk.runbody;

/**
 * 러닝 선택 목록(G-30) 한 줄의 분석 상태와 러닝 점수.
 *
 * @param recordId 기록 ID
 * @param status 작업 상태
 * @param score 러닝 점수(RunBodyScore). 아직 없으면 null
 * @param scoreChange 이 회원의 직전 점수 있는 러닝 대비 변화(양수면 올랐다). 이 러닝이나 이전 러닝에 점수가 없으면 null
 */
public record RunBodySessionItem(
        String recordId, RunBodyStatus status, Integer score, Integer scoreChange) {}

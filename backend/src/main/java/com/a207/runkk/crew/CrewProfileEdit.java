package com.a207.runkk.crew;

/**
 * 크루 내 표시 정보 변경(자료사전 크루내정보변경요청, FR-144). v0.11에서 성향 태그를 지웠다(D-91).
 *
 * @param intro 크루 내 한줄 소개, 40자 이하
 */
public record CrewProfileEdit(String intro) {}

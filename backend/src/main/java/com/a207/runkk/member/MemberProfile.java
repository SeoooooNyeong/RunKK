package com.a207.runkk.member;

/**
 * 프로필 화면이 읽는 회원 자기소개 항목(FR-005, 자료사전 프로필의 레벨·프로필사진·프로필소개·나이대·성별). MemberSummary·MemberPreference에
 * 없는 항목만 담은 좁은 투영이다(D-41 방식, B5 결정: 설계 6.1.5는 summaries·preferenceOf만 적어 FR-005 항목이 빠져 있었다).
 *
 * @param level 레벨
 * @param photoUrl 프로필 사진 주소, 없으면 null
 * @param intro 소개, 없으면 null
 * @param ageBand 나이대("20대"), 출생연도가 없으면 null
 * @param gender 성별, 입력하지 않았으면 null
 */
public record MemberProfile(
        int level, String photoUrl, String intro, String ageBand, Gender gender) {}

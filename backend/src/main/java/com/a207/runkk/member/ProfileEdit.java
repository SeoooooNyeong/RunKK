package com.a207.runkk.member;

/**
 * 프로필 변경 요청(자료사전 프로필변경요청, FR-007). null인 항목은 바꾸지 않는다. 사진·소개를 지우려면 빈 문자열을 보낸다. v0.11에서 성향 태그를
 * 지웠다(D-91).
 *
 * @param nickname 새 닉네임
 * @param birthYear 출생연도(4자리, 만 14세 이하면 UNDER_14)
 * @param gender 성별
 * @param photoUrl 프로필 사진 주소
 * @param intro 프로필 소개(40자 이하)
 */
public record ProfileEdit(
        String nickname, Integer birthYear, Gender gender, String photoUrl, String intro) {}

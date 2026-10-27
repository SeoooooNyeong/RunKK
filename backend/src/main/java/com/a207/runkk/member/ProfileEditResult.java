package com.a207.runkk.member;

/**
 * 프로필 편집 뒤의 본인 프로필 항목(FR-007). v0.11에서 성향 태그를 지웠다(D-91).
 *
 * @param memberId 회원 ID
 * @param nickname 닉네임
 * @param birthYear 출생연도
 * @param gender 성별
 * @param photoUrl 사진 주소
 * @param intro 소개
 */
public record ProfileEditResult(
        long memberId,
        String nickname,
        Integer birthYear,
        Gender gender,
        String photoUrl,
        String intro) {}

package com.a207.runkk.member;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.common.Tier;

/**
 * 다른 패키지가 회원 여러 명의 표시 정보를 한 번에 읽는 조회 전용 값(설계 6.1.2, N+1 방지 6.5). 탈퇴 회원은 DisplayName.WITHDRAWN.
 *
 * @param id 회원 ID
 * @param displayName 표시명
 * @param tier 티어
 * @param profileVisibility 개인정보 공개
 * @param tags 성향 태그
 */
public record MemberSummary(
        long id,
        DisplayName displayName,
        Tier tier,
        ProfileVisibility profileVisibility,
        TagSet tags) {}

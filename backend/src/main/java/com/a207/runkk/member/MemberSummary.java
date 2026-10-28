package com.a207.runkk.member;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Tier;

/**
 * 다른 패키지가 회원 여러 명의 표시 정보를 한 번에 읽는 조회 전용 값(설계 6.1.2, N+1 방지 6.5). 탈퇴 회원은 DisplayName.WITHDRAWN.
 * v0.11에서 성향 태그를 지웠다(12.2 D22, D-91). 계정 유형은 싣지 않는다(FR-006).
 *
 * @param id 회원 ID
 * @param displayName 표시명
 * @param tier 티어
 * @param profileVisibility 개인정보 공개
 */
public record MemberSummary(
        long id, DisplayName displayName, Tier tier, ProfileVisibility profileVisibility) {}

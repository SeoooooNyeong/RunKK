package com.a207.runkk.member;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Tier;

/**
 * 고스트 기록 주인이 될 수 있는 회원 한 명(설계 6.1.2, A-07).
 *
 * @param memberId 회원 ID
 * @param displayName 표시명
 * @param tier 티어
 */
public record GhostOwner(long memberId, DisplayName displayName, Tier tier) {}

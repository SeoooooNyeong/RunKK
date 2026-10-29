package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 초대 링크를 연 러너에게 보일 초대 정보(설계 5.6.2, D-224). 끝난 초대도 오류가 아니라 status·blockedReason으로 알린다.
 *
 * @param inviteId 초대 ID
 * @param status 지금 상태(만료 판정 뒤)
 * @param distance 대결 거리
 * @param expiresAt 만료 시각
 * @param inviterName 초대한 러너 표시명
 * @param inviterTier 초대한 러너 티어
 * @param inviterBest 초대한 러너의 그 거리 최고 기록(없으면 null)
 * @param myTier 조회한 러너 티어
 * @param myBest 조회한 러너의 그 거리 최고 기록(없으면 null)
 * @param blockedReason 참가할 수 없는 사유(없으면 null)
 */
public record FriendInviteView(
        long inviteId,
        FriendInviteStatus status,
        StandardDistance distance,
        LocalDateTime expiresAt,
        DisplayName inviterName,
        Tier inviterTier,
        Duration inviterBest,
        Tier myTier,
        Duration myBest,
        ReasonCode blockedReason) {}

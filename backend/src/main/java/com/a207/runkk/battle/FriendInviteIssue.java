package com.a207.runkk.battle;

import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;

/**
 * 만든(또는 열려 있는) 친구 대결 초대(설계 5.6.2). 링크 주소는 표현 계층(web.battle.FriendInviteLinks)이 토큰으로 만든다.
 *
 * @param inviteId 초대 ID
 * @param token 링크 토큰(22자)
 * @param distance 대결 거리
 * @param expiresAt 만료 시각(생성 + 5분)
 */
public record FriendInviteIssue(
        long inviteId, String token, StandardDistance distance, LocalDateTime expiresAt) {

    static FriendInviteIssue of(FriendInvite invite) {
        return new FriendInviteIssue(
                invite.getId(), invite.getToken(), invite.getDistance(), invite.getExpiresAt());
    }
}

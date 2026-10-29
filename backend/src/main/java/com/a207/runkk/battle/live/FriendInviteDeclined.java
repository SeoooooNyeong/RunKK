package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;

/**
 * 받는 러너가 친구 대결 초대를 거절했다는 알림(설계 5.6.3, D-223). 초대한 앱에만 간다.
 *
 * @param inviteId 초대 ID
 */
public record FriendInviteDeclined(long inviteId) implements BattleMessage {

    @Override
    public String type() {
        return "FriendInviteDeclined";
    }
}

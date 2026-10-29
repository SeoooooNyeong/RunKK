package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;

/**
 * 친구 대결 초대가 5분 안에 참가되지 않아 만료되었다는 알림(설계 5.6.3, D-221). 초대한 앱에만 간다.
 *
 * @param inviteId 초대 ID
 */
public record FriendInviteExpired(long inviteId) implements BattleMessage {

    @Override
    public String type() {
        return "FriendInviteExpired";
    }
}

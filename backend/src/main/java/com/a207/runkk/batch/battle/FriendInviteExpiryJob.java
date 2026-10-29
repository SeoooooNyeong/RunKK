package com.a207.runkk.batch.battle;

import com.a207.runkk.battle.FriendInviteService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 5초마다 기한 지난 친구 대결 초대를 만료한다(설계 5.6.2, D-221): FriendInviteService.expireDue(now). 업무 규칙이 없고 실패는 운영
 * 로그로 남긴다. 판정의 정확성은 FriendInvite.statusAt(조회 시 판정)이 맡고 이 작업은 저장과 알림만 한다.
 */
@Component
public class FriendInviteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(FriendInviteExpiryJob.class);

    private final FriendInviteService friendInviteService;
    private final Clock clock;

    public FriendInviteExpiryJob(FriendInviteService friendInviteService, Clock clock) {
        this.friendInviteService = friendInviteService;
        this.clock = clock;
    }

    public void run() {
        try {
            friendInviteService.expireDue(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("friend_invite_expiry_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}

package com.a207.runkk.battle;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 한 러너가 새 대결을 시작할 수 없는 상태인지 한 곳에서 판정한다(설계 5.6.2, D-220): 매칭 대기 표, 활성 대결, 열린 대기 초대. 랜덤 매칭
 * 요청(MatchingService)과 친구 대결 초대·참가(FriendInviteService·FriendInviteQuery)가 쓴다. 대기열 장애는
 * DataAccessException으로 올리고 부르는 쪽이 QUEUE_UNAVAILABLE로 바꾼다.
 */
@Component
public class RunnerAvailability {

    private final MatchQueue matchQueue;
    private final BattleRepository battleRepository;
    private final FriendInviteRepository friendInviteRepository;

    public RunnerAvailability(
            MatchQueue matchQueue,
            BattleRepository battleRepository,
            FriendInviteRepository friendInviteRepository) {
        this.matchQueue = matchQueue;
        this.battleRepository = battleRepository;
        this.friendInviteRepository = friendInviteRepository;
    }

    /** 매칭 대기 표 또는 활성 대결 또는 열린 대기 초대가 있으면 참. */
    public boolean isBusy(long memberId, LocalDateTime now) {
        return isInBattleOrQueue(memberId) || friendInviteRepository.existsOpen(memberId, now);
    }

    /** 매칭 대기 표 또는 활성 대결이 있으면 참(초대한 쪽 확인용, 자기 초대는 세지 않는다, D-222). */
    public boolean isInBattleOrQueue(long memberId) {
        return matchQueue.findWaiting(memberId).isPresent()
                || battleRepository.existsActive(memberId);
    }
}

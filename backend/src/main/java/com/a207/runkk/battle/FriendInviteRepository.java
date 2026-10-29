package com.a207.runkk.battle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 친구 대결 초대 저장소 포트(설계 5.6.2). 구현은 infra.battle.JpaFriendInviteRepository. battle 밖에서 쓰지 않는다(13.1 규칙
 * 4).
 */
public interface FriendInviteRepository {

    FriendInvite save(FriendInvite invite);

    /** 저장하고 바로 저장소에 쓴다. 대기 초대 부분 고유 인덱스 위반을 그 자리에서 받는다. */
    FriendInvite saveImmediately(FriendInvite invite);

    Optional<FriendInvite> findById(long id);

    Optional<FriendInvite> findByToken(String token);

    /** 그 회원이 보낸 PENDING 초대(만료 시각과 무관). 부분 고유 인덱스로 많아야 하나다. */
    Optional<FriendInvite> findPending(long inviterId);

    /** 그 회원이 보낸 열린 대기 초대(PENDING이고 now &lt; expiresAt)가 있는가. */
    boolean existsOpen(long inviterId, LocalDateTime now);

    /** 기한이 지난(expiresAt ≤ now) PENDING 초대, 만료 시각 순으로 limit개. */
    List<FriendInvite> findDuePending(LocalDateTime now, int limit);
}

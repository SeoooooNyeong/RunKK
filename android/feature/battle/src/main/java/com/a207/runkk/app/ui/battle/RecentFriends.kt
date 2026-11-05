package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.domain.battle.BattleHistory
import java.time.ZoneId

/** 최근 함께 달린 친구 고르기(J24-18, D-492). */
object RecentFriends {
    /**
     * 친구 대결 이력 행 가운데 상대 회원 ID가 있는 것(탈퇴·기록 상대 제외)을 최근 순으로 보고 같은 회원은 가장 최근 한 번만 남겨 앞의 [limit]명.
     */
    fun of(
        entries: List<BattleHistory.Entry>,
        limit: Int = 3,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<RecentFriend> = entries
        .filter { it.opponentMemberId != null }
        .sortedByDescending { it.playedAt }
        .distinctBy { it.opponentMemberId }
        .take(limit)
        .map {
            RecentFriend(
                requireNotNull(it.opponentMemberId),
                it.opponentName,
                it.playedAt.atZone(zone).toLocalDate()
            )
        }
}

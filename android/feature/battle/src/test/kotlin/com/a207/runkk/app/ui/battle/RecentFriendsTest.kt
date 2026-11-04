package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.BattleHistory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** 최근 함께 달린 친구(FR-286, J24-18, D-492): 친구 대결 이력에서 최근 순 3명, 같은 회원은 한 번, 탈퇴(회원 ID 없음)는 뺀다. */
class RecentFriendsTest {
    private fun entry(id: Long, name: String, memberId: Long?, month: Int, day: Int) = BattleHistory.Entry(
        battleId = id,
        type = BattleHistory.Type.FRIEND,
        opponentName = name,
        distance = Distance(3_000),
        verdict = "WIN",
        playedAt = LocalDateTime.of(2026, month, day, 7, 0).toInstant(ZoneOffset.UTC),
        opponentMemberId = memberId
    )

    @Test
    fun `TC-5888 최근 함께 달린 친구는 최근 순 3명이고 중복과 탈퇴를 뺀다`() {
        val entries = listOf(
            entry(1, "D", 4, 10, 29),
            entry(2, "A", 1, 11, 1),
            entry(3, "탈퇴", null, 11, 4),
            entry(4, "B", 2, 11, 2),
            entry(5, "C", 3, 10, 30),
            entry(6, "A", 1, 11, 3)
        )

        val friends = RecentFriends.of(entries, zone = ZoneOffset.UTC)

        assertEquals(
            listOf(
                RecentFriend(1, "A", LocalDate.of(2026, 11, 3)),
                RecentFriend(2, "B", LocalDate.of(2026, 11, 2)),
                RecentFriend(3, "C", LocalDate.of(2026, 10, 30))
            ),
            friends
        )
    }

    @Test
    fun `TC-5889 친구가 셋보다 적으면 있는 만큼만 보인다`() {
        assertEquals(emptyList(), RecentFriends.of(emptyList(), zone = ZoneOffset.UTC))
        val two = RecentFriends.of(listOf(entry(1, "A", 1, 11, 1), entry(2, "B", 2, 11, 2)), zone = ZoneOffset.UTC)
        assertEquals(listOf(2L, 1L), two.map { it.memberId })
    }
}

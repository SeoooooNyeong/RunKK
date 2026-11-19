package com.a207.runkk.app.ui.battle

import android.app.Activity
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.a207.runkk.app.core.navigation.EntryTransition
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.FriendInvite
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 대결 경로 등록(S15P31A207-335 8회차): 대결 진행 화면(출발 카운트다운)은 앞 화면과 0.7초 엇갈려 나타나지 않고 바로 그린다.
 * 다른 대결 화면은 기본 넘어가기 그대로다.
 */
class BattleFeatureEntryTest {
    private val sharer = object : InviteSharer {
        override suspend fun share(activity: Activity, invite: FriendInvite, inviterName: String?) = ShareResult.FAILED
    }

    private val provider = entryProvider<NavKey> { BattleFeatureEntry(sharer).register(this) }

    @Test
    fun `대결 진행 경로는 넘어가기 없이 바로 그린다`() {
        val entry = provider(Routes.BattleRunning(42, "과거의 나", 1_000, OpponentKind.SELF.name))
        assertTrue(EntryTransition.isInstant(entry.metadata))
    }

    @Test
    fun `대결 결과와 기록런 탭은 기본 넘어가기다`() {
        assertFalse(EntryTransition.isInstant(provider(Routes.BattleResult(42, isJustFinished = true)).metadata))
        assertFalse(EntryTransition.isInstant(provider(Routes.Key(Routes.RECORD_RUN)).metadata))
    }
}

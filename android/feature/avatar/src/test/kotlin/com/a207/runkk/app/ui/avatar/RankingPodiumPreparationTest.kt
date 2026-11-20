package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.SelfieHead
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RunnerLook
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 랭킹 시상대 아바타 미리 준비(S15P31A207-345 9회차): 로딩 화면에서 1~3위가 시상대 칸과 같은 모습(같은 머리 인스턴스)으로 정지 그림
 * 기기 사본을 캐시에 올리고, 사본이 없으면 그 모습의 서기 합성을 미리 한다. 나는 홈과 같은 내 모습으로 올린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RankingPodiumPreparationTest {
    private val repo = FakeAvatars().apply { headResult = Outcome.Success(SelfieHead(faceLayers)) }
    private val stilled = mutableListOf<RiveAvatarLook>()
    private val composed = mutableListOf<RiveAvatarLook>()
    private var savedStills = 0

    private fun TestScope.store() = MyAvatarStore(
        avatars = repo,
        myGender = MutableStateFlow(Gender.FEMALE),
        heads = headsOf(repo),
        preload = {},
        prepare = { _, _ -> },
        scope = backgroundScope
    )

    private fun TestScope.preparation(ranking: Ranking?, store: MyAvatarStore = store()) = RankingPodiumPreparation(
        ranking = { ranking },
        store = store,
        heads = store.heads,
        prepareLook = { composed += it },
        loadStills = { look ->
            stilled += look
            savedStills
        },
        main = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `시상대 1~3위를 칸과 같은 모습으로 올리고 그림 사본이 없으면 서기 합성을 미리 한다`() = runTest {
        val preparation = preparation(RANKING)
        var steps = 0

        assertFalse(preparation.isPrepared())
        assertEquals(Outcome.Success(Unit), preparation.prepare { steps++ })

        assertEquals(3, steps, "시상대 자리 셋")
        assertTrue(preparation.isPrepared())
        val first = runnerLookOf(RankingPodiumPreparation.subjectOf(RANKING.entries[0]), null)
        assertTrue(first in stilled, "칸(runnerLookOf)과 같은 모습으로 정지 그림 사본을 찾는다")
        assertEquals(AvatarGender.FEMALE, first.gender)
        assertEquals(40, first.runningAmount)
        assertTrue(first in composed, "사본이 없으면 칸이 기다리지 않게 서기 합성을 미리 한다")
        assertEquals(3, stilled.size, "나 포함 셋")
    }

    @Test
    fun `나는 홈과 같은 내 모습(같은 머리 인스턴스)으로 올린다`() = runTest {
        val store = store()
        val preparation = preparation(RANKING, store)
        preparation.prepare {}

        val ready = assertIs<MyAvatarState.Ready>(store.state.value)
        assertTrue(stilled.any { it === ready.look.rive }, "내 아바타 저장소 모습 그대로")
        assertFalse(composed.any { it === ready.look.rive }, "내 합성은 홈 아바타 준비가 한다")
    }

    @Test
    fun `그림 사본이 있으면 합성하지 않는다`() = runTest {
        savedStills = 1
        preparation(RANKING).prepare {}
        assertTrue(composed.isEmpty())
    }

    @Test
    fun `랭킹을 받지 못하고 사본도 없으면 다시 받을 만한 실패다`() = runTest {
        val preparation = preparation(null)
        assertEquals(Outcome.Failure(AppError.Network), preparation.prepare {})
        assertFalse(preparation.isPrepared())
    }

    @Test
    fun `시상대 칸은 시작 로딩이 받아 둔 머리를 기다리지 않고 첫 판부터 같은 인스턴스로 쓴다`() = runTest {
        val heads = headsOf(repo)
        assertNull(heads.peek("head:9:job-2"), "받기 전에는 없다")
        val loaded = heads.look("head:9:job-2")
        assertSame(loaded, heads.peek("head:9:job-2"))
        assertSame(FaceLook.NONE, heads.peek(null))
    }

    private companion object {
        val MINE = Ranking.Entry(2, "나", Distance(30_000), Gender.FEMALE)
        val RANKING = Ranking(
            entries = listOf(
                Ranking.Entry(
                    1,
                    "바람",
                    Distance(42_000),
                    Gender.FEMALE,
                    look = RunnerLook(null, null, null, null, bodyVariant = 1, runAmount = 40)
                ),
                MINE,
                Ranking.Entry(3, "구름", Distance(20_000), Gender.MALE)
            ),
            mine = MINE,
            gapToNext = null,
            aggregatedAt = null,
            notice = null
        )
    }
}

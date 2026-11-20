package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.SelfieHead
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 홈 큰 아바타 미리 준비(S15P31A207-345 2차): 시작 로딩에서 홈 칸과 같은 내 아바타 저장소를 데워 서기 합성을 공용 캐시에 넣고(같은 모습·같은
 * 머리 인스턴스 = 홈 칸이 찾는 캐시 열쇠), 홈 칸이 쓸 서기 Rive 파일을 붙잡는다. 홈 칸이 열리면 같은 모습으로 다시 합성을 찾으므로 캐시에
 * 적중하고 로딩 원 없이 바로 그린다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAvatarPreparationTest {
    private val repo = FakeAvatars().apply {
        avatarResult = Outcome.Success(avatar.copy(faceKey = "head:7:job-1"))
        runnerResult = Outcome.Success(AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4))
        headResult = Outcome.Success(SelfieHead(faceLayers))
    }
    private val gender = MutableStateFlow<Gender?>(Gender.FEMALE)
    private val prepared = mutableListOf<Pair<RiveAvatarLook, AvatarMotion>>()
    private val held = mutableListOf<AvatarGender>()

    private fun TestScope.store() = MyAvatarStore(
        avatars = repo,
        myGender = gender,
        heads = headsOf(repo),
        preload = {},
        prepare = { look, motion -> prepared += look to motion },
        scope = backgroundScope
    )

    private fun TestScope.preparation(store: MyAvatarStore) = HomeAvatarPreparation(
        store,
        holdRive = { gender ->
            held += gender
            awaitCancellation()
        },
        scope = backgroundScope,
        main = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `로딩에서 홈 칸과 같은 모습으로 서기 합성을 미리 하고 홈 칸 Rive 파일을 붙잡는다`() = runTest {
        val store = store()
        val preparation = preparation(store)
        var steps = 0

        assertFalse(preparation.isPrepared())
        assertEquals(Outcome.Success(Unit), preparation.prepare { steps++ })
        runCurrent()

        assertEquals(2, steps, "입력·합성 1 + Rive 첫 판 1")
        val ready = assertIs<MyAvatarState.Ready>(store.state.value, "홈이 뜨기 전에 준비가 끝난다")
        val stand = prepared.first { it.second == AvatarMotion.STAND }.first
        assertSame(ready.look.rive, stand, "홈 칸이 그릴 모습과 같은 것으로 미리 합성했다")
        assertEquals(listOf(AvatarGender.FEMALE), held, "홈 칸의 성별 서기 파일을 붙잡는다")
        assertTrue(preparation.isPrepared())

        // 홈 칸이 열린다(HomeAvatarSlotContent: warm + revalidate). 같은 모습(같은 머리 인스턴스)으로 다시 찾으므로 공용 캐시에 적중한다.
        prepared.clear()
        store.warm()
        store.revalidate()
        runCurrent()
        assertIs<MyAvatarState.Ready>(store.state.value, "다시 읽는 동안에도 로딩으로 돌아가지 않는다")
        val again = prepared.first { it.second == AvatarMotion.STAND }.first
        assertEquals(stand, again, "같은 옷·체형·그을림")
        assertSame(stand.headLayers, again.headLayers, "같은 머리 인스턴스(공용 캐시는 참조로 찾는다)")
    }

    @Test
    fun `붙잡은 Rive 파일은 시간이 지나면 놓는다`() = runTest {
        val preparation = preparation(store())
        preparation.prepare {}
        runCurrent()
        assertTrue(preparation.isPrepared())
        advanceTimeBy(HomeAvatarPreparation.RIVE_HOLD_MILLIS + 1)
        runCurrent()
        assertFalse(preparation.isPrepared())
    }

    @Test
    fun `입력을 읽지 못하면 다시 받을 만한 실패를 돌려주고 다시 하면 이어서 준비한다`() = runTest {
        repo.runnerResult = Outcome.Failure(AppError.Network)
        val preparation = preparation(store())
        assertEquals(Outcome.Failure(AppError.Network), preparation.prepare {})
        assertTrue(held.isEmpty())

        repo.runnerResult = Outcome.Success(AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4))
        assertEquals(Outcome.Success(Unit), preparation.prepare {}, "다시 시도로 이어 준비한다")
    }

    @Test
    fun `서기 합성이 예외로 끝나면 실패로 돌려주고 다시 시도하면 다시 합성한다`() = runTest {
        var failures = 1
        val looks = mutableListOf<RiveAvatarLook>()
        val preparation = HomeAvatarPreparation(
            store(),
            holdRive = { awaitCancellation() },
            scope = backgroundScope,
            main = StandardTestDispatcher(testScheduler),
            prepareLook = { look ->
                looks += look
                if (failures-- > 0) throw IllegalStateException("compose failed")
            }
        )
        var steps = 0
        assertEquals(Outcome.Failure(AppError.Network), preparation.prepare { steps++ })
        assertEquals(0, steps, "합성을 마치지 못하면 단계를 올리지 않는다")
        assertFalse(preparation.isPrepared())

        assertEquals(Outcome.Success(Unit), preparation.prepare { steps++ })
        assertEquals(2, steps)
        assertEquals(2, looks.size, "다시 시도하면 다시 합성한다")
        assertSame(looks[0], looks[1], "같은 모습(홈 칸 열쇠)")
    }

    @Test
    fun `성별을 모르면 프로필을 물어 알게 하고 받지 못하면 다시 받을 만한 실패다`() = runTest {
        gender.value = null
        var asked = 0
        val preparation = HomeAvatarPreparation(
            store(),
            holdRive = { awaitCancellation() },
            scope = backgroundScope,
            main = StandardTestDispatcher(testScheduler),
            knowGender = {
                asked++
                Outcome.Failure(AppError.Network)
            }
        )
        assertEquals(Outcome.Failure(AppError.Network), preparation.prepare {})
        assertEquals(1, asked)
    }

    @Test
    fun `성별을 아주 오래 몰라도 시간으로 끊지 않고 알 때까지 기다린다(시작 로딩 시간 상한 없음)`() = runTest {
        gender.value = null
        val preparation = preparation(store())
        val result = async { preparation.prepare {} }
        advanceTimeBy(ONE_DAY_MILLIS)
        runCurrent()
        assertFalse(result.isCompleted, "하루가 지나도 실패·건너뛰기 없이 기다린다")

        gender.value = Gender.FEMALE
        runCurrent()
        assertEquals(Outcome.Success(Unit), result.await())
    }

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1_000
    }
}

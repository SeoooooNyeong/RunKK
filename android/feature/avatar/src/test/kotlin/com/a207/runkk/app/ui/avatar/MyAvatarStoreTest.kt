package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 내 아바타 저장소(design-oo 05-30 [임시:D-성-103], [임시:D-성-104], 3절 상태도, 11절 3번): 입력을 한 곳에 모아 준비하고, 세대 번호로
 * 늦게 온 앞 작업 결과를 버리며, 로그아웃(clear) 뒤에는 앞 회원 그림을 보이지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyAvatarStoreTest {
    private val repo = GatedAvatars()
    private val gender = MutableStateFlow<Gender?>(null)
    private val preloaded = mutableListOf<AvatarGender>()
    private val prepared = mutableListOf<RiveAvatarLook>()
    private var prepareFailure: Exception? = null

    private fun TestScope.store() = MyAvatarStore(
        avatars = repo,
        myGender = gender,
        heads = headsOf(repo),
        preload = { preloaded += it },
        prepare = { look ->
            prepareFailure?.let { throw it }
            prepared += look
        },
        scope = backgroundScope
    )

    @Test
    fun `처음은 Idle이고 warm 뒤 성별을 모르면 Loading에 머물며 아무것도 읽지 않는다`() = runTest {
        val store = store()
        assertIs<MyAvatarState.Idle>(store.state.value)

        store.warm()
        runCurrent()

        assertIs<MyAvatarState.Loading>(store.state.value)
        assertTrue(repo.base.calls.isEmpty(), "성별을 알기 전에는 그림을 고르지 않는다(FR-162 CR-86)")
        assertTrue(preloaded.isEmpty())
    }

    @Test
    fun `성별을 알면 riv를 미리 읽고 세 입력을 모아 서기 모습을 미리 준비한 뒤 Ready다`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()

        val look = assertIs<MyAvatarState.Ready>(store.state.value).look
        assertEquals(AvatarGender.FEMALE, look.rive.gender)
        assertEquals(1, look.rive.outfit.top, "착용 상의(아이템 1 = top_lime_singlet 자리)")
        assertEquals(AvatarBodyShape.entries.first { it.stage == 1 }, look.rive.bodyShape, "체형은 runner.bodyVariant")
        assertEquals(51, look.rive.runningAmount)
        assertEquals("INCHEON", look.backgroundKey)
        assertEquals(listOf(AvatarGender.FEMALE), preloaded)
        assertEquals(listOf(look.rive), prepared, "칸에 넘길 것과 같은 모습을 미리 준비한다")
        assertSame(look.rive, prepared.single())
    }

    @Test
    fun `비공개 성별은 남자 몸체로 준비한다`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.UNDISCLOSED
        runCurrent()

        assertEquals(AvatarGender.MALE, assertIs<MyAvatarState.Ready>(store.state.value).look.rive.gender)
    }

    @Test
    fun `아바타를 못 읽으면 Failed이고 칸 진입에서 한 번 다시 시도한다`() = runTest {
        repo.base.avatarResult = Outcome.Failure(AppError.Network)
        val store = store()
        store.warm()
        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Failed>(store.state.value)

        repo.base.avatarResult = Outcome.Success(sample)
        store.retryIfFailed()
        runCurrent()

        assertIs<MyAvatarState.Ready>(store.state.value)
    }

    @Test
    fun `미리 준비가 실패해도 그림은 그린다(뷰가 다시 합성한다)`() = runTest {
        prepareFailure = IOException("riv")
        val store = store()
        store.warm()
        gender.value = Gender.MALE
        runCurrent()

        assertIs<MyAvatarState.Ready>(store.state.value)
    }

    @Test
    fun `clear 뒤 늦게 끝난 앞 회원의 결과는 버리고 Idle을 지킨다`() = runTest {
        val late = repo.holdNextAvatar()
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()
        assertIs<MyAvatarState.Loading>(store.state.value)

        store.clear()
        late.complete(Outcome.Success(sample))
        runCurrent()

        assertIs<MyAvatarState.Idle>(store.state.value)
        assertTrue(prepared.isEmpty())
    }

    @Test
    fun `clear 뒤 다시 warm하면 성별 구독을 새로 한다`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()
        store.clear()
        assertIs<MyAvatarState.Idle>(store.state.value)

        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Idle>(store.state.value, "clear 뒤에는 성별이 바뀌어도 준비하지 않는다")

        store.warm()
        runCurrent()
        assertEquals(AvatarGender.MALE, assertIs<MyAvatarState.Ready>(store.state.value).look.rive.gender)
    }

    @Test
    fun `refresh 동안 앞 Ready를 그대로 두고 새 입력이 오면 바꾼다`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()
        val before = assertIs<MyAvatarState.Ready>(store.state.value)

        val next = repo.holdNextAvatar()
        store.refresh()
        runCurrent()
        assertSame(before, store.state.value, "다시 읽는 동안 로딩 표시 없음")

        next.complete(Outcome.Success(sample.copy(backgroundKey = "BUSAN")))
        runCurrent()
        assertEquals("BUSAN", assertIs<MyAvatarState.Ready>(store.state.value).look.backgroundKey)
    }

    @Test
    fun `겹친 refresh는 마지막 것이 이긴다`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()

        val first = repo.holdNextAvatar()
        store.refresh()
        runCurrent()
        val second = repo.holdNextAvatar()
        store.refresh()
        runCurrent()
        second.complete(Outcome.Success(sample.copy(backgroundKey = "SECOND")))
        first.complete(Outcome.Success(sample.copy(backgroundKey = "FIRST")))
        runCurrent()

        assertEquals("SECOND", assertIs<MyAvatarState.Ready>(store.state.value).look.backgroundKey)
    }

    @Test
    fun `성별이 바뀌면 앞 그림을 버리고 새 성별로 다시 준비한다`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()

        val held = repo.holdNextAvatar()
        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Loading>(store.state.value, "다른 성별 그림을 보이지 않는다")

        held.complete(Outcome.Success(sample))
        runCurrent()
        assertEquals(AvatarGender.MALE, assertIs<MyAvatarState.Ready>(store.state.value).look.rive.gender)
        assertEquals(listOf(AvatarGender.FEMALE, AvatarGender.MALE), preloaded)
    }

    @Test
    fun `revalidate는 Ready·Failed일 때만 다시 읽고 준비 중에는 그대로 둔다`() = runTest {
        val held = repo.holdNextAvatar()
        val store = store()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()

        store.revalidate()
        runCurrent()
        held.complete(Outcome.Success(sample))
        runCurrent()
        assertEquals(1, repo.avatarCalls, "warm이 읽는 동안 칸 진입은 다시 읽지 않는다")
        assertIs<MyAvatarState.Ready>(store.state.value)

        store.revalidate()
        runCurrent()
        assertEquals(2, repo.avatarCalls, "Ready면 칸 진입에서 다시 읽는다(꾸미기 뒤 홈 복귀)")
    }

    @Test
    fun `warm을 두 번 불러도 한 번만 준비한다`() = runTest {
        val store = store()
        store.warm()
        store.warm()
        gender.value = Gender.FEMALE
        runCurrent()

        assertEquals(1, repo.avatarCalls)
    }
}

private val sample = Avatar(
    faceKey = null,
    defaultCharacterId = "FOX",
    bodyShape = "STAGE3",
    skinTone = "MORNING",
    equipped = mapOf(ItemSlot.TOP to 1L),
    balance = 1_240,
    canCreateSelfieToday = true,
    rejected = emptyMap(),
    backgroundKey = "INCHEON"
)

/** [FakeAvatars]에 아바타 읽기를 붙잡아 두는 문을 단다(늦게 온 응답 시험). */
private class GatedAvatars(val base: FakeAvatars = FakeAvatars()) : AvatarRepository by base {
    private val held = ArrayDeque<CompletableDeferred<Outcome<Avatar>>>()
    var avatarCalls = 0
        private set

    init {
        base.avatarResult = Outcome.Success(sample)
        base.runnerResult = Outcome.Success(AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4))
    }

    /** 다음 아바타 읽기는 돌려준 값이 정해질 때까지 기다린다. */
    fun holdNextAvatar(): CompletableDeferred<Outcome<Avatar>> =
        CompletableDeferred<Outcome<Avatar>>().also(held::addLast)

    /** 착용 이름을 그림 키로 옮기려면 상점 목록의 그림 키가 필요하다(아이템 1 = 라임 싱글렛, Rive 상의 1). */
    override suspend fun shop(slot: ItemSlot?): Outcome<Shop> =
        Outcome.Success(Shop(listOf(Shop.Item(1, ItemSlot.TOP, "라임 싱글렛", 300, true, true, "top_lime_singlet")), 0))

    override suspend fun avatar(): Outcome<Avatar> {
        avatarCalls++
        val gate = held.removeFirstOrNull() ?: return base.avatar()
        return gate.await()
    }
}

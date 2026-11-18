package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.SelfieHead
import com.a207.runkk.app.domain.avatar.SelfieProgress
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
 * 내 아바타 저장소(design-oo 05-30 D-735, D-736, 3절 상태도, 11절 3번): 입력을 한 곳에 모아 준비하고, 세대 번호로
 * 늦게 온 앞 작업 결과를 버리며, 로그아웃(clear) 뒤에는 앞 회원 그림을 보이지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyAvatarStoreTest {
    private val repo = GatedAvatars()
    private val gender = MutableStateFlow<Gender?>(null)
    private val preloaded = mutableListOf<AvatarGender>()
    private val prepared = mutableListOf<Pair<RiveAvatarLook, AvatarMotion>>()
    private var prepareFailure: Exception? = null
    private val loggedIn = MutableStateFlow(true)
    private var loggedOutCleanups = 0
    private var resumes = 0
    private var checks = 0

    /** 기기에 남긴 성별(D-956)과 남긴 기록. */
    private var savedGender: Gender? = null
    private val savedGenders = mutableListOf<Gender>()

    private fun TestScope.store(savedFaceKey: String? = null) = MyAvatarStore(
        avatars = repo,
        myGender = gender,
        heads = headsOf(repo),
        savedFaceKey = { savedFaceKey },
        savedGender = { savedGender },
        saveGender = { savedGenders += it },
        preload = { preloaded += it },
        prepare = { look, motion ->
            prepareFailure?.let { throw it }
            prepared += look to motion
        },
        scope = backgroundScope,
        loggedIn = loggedIn,
        onLoggedOut = { loggedOutCleanups++ },
        resumeSelfie = { resumes++ },
        checkPending = { checks++ }
    )

    @Test
    fun `로그아웃(세션)에 비우고 보낸 사진을 지우며, 로그아웃 동안 warm은 아무것도 하지 않는다(리뷰 R2-7, R2-11)`() = runTest {
        val store = store()
        store.warm()
        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Ready>(store.state.value)

        loggedIn.value = false
        runCurrent()
        assertIs<MyAvatarState.Idle>(store.state.value)
        assertEquals(1, loggedOutCleanups)

        store.warm()
        runCurrent()
        assertIs<MyAvatarState.Idle>(store.state.value, "로그아웃 뒤 늦게 온 warm은 앞 회원 그림을 다시 준비하지 않는다")

        loggedIn.value = true
        store.warm()
        runCurrent()
        assertIs<MyAvatarState.Ready>(store.state.value)
    }

    @Test
    fun `warm은 프로필(성별)을 기다리지 않고 기기에 남긴 내 얼굴 키로 머리를 바로 받아 합성을 시작한다(S15P31A207-300)`() = runTest {
        repo.base.headResult = Outcome.Success(SelfieHead(faceLayers))
        repo.base.avatarResult = Outcome.Success(sample.copy(faceKey = "head:7:job-1"))
        val store = store(savedFaceKey = "head:7:job-1")

        store.warm()
        runCurrent()
        assertEquals(listOf("head:head:7:job-1"), repo.base.calls.filter { it.startsWith("head:") }, "성별을 모르는 동안 받는다")
        assertIs<MyAvatarState.Loading>(store.state.value)

        gender.value = Gender.MALE
        runCurrent()
        val ready = assertIs<MyAvatarState.Ready>(store.state.value)
        assertEquals(faceLayers, ready.look.faceLayers)
        assertEquals(1, repo.base.calls.count { it.startsWith("head:") }, "같은 머리를 다시 받지 않는다")
    }

    @Test
    fun `기기에 남긴 성별이 있으면 프로필(성별) 응답을 기다리지 않고 바로 합성해 Ready가 된다(Jira 307)`() = runTest {
        savedGender = Gender.FEMALE
        repo.base.headResult = Outcome.Success(SelfieHead(faceLayers))
        repo.base.avatarResult = Outcome.Success(sample.copy(faceKey = "head:7:job-1"))
        val store = store(savedFaceKey = "head:7:job-1")

        store.warm()
        runCurrent()
        val ready = assertIs<MyAvatarState.Ready>(store.state.value, "성별 흐름은 아직 null이다")
        assertEquals(AvatarGender.FEMALE, ready.look.rive.gender)
        assertEquals(faceLayers, ready.look.faceLayers)

        gender.value = Gender.FEMALE
        runCurrent()
        assertEquals(1, prepared.count { it.second == AvatarMotion.STAND }, "같은 성별이면 다시 합성하지 않는다")
        assertEquals(listOf(Gender.FEMALE), savedGenders, "알게 된 성별을 기기에 남긴다")
    }

    @Test
    fun `프로필이 남긴 것과 다른 성별을 주면 다시 합성한다(Jira 307)`() = runTest {
        savedGender = Gender.FEMALE
        val store = store()
        store.warm()
        runCurrent()
        assertEquals(AvatarGender.FEMALE, assertIs<MyAvatarState.Ready>(store.state.value).look.rive.gender)

        gender.value = Gender.MALE
        runCurrent()
        assertEquals(AvatarGender.MALE, assertIs<MyAvatarState.Ready>(store.state.value).look.rive.gender)
        assertEquals(listOf(Gender.MALE), savedGenders)
    }

    @Test
    fun `남긴 얼굴 키가 셀카 머리가 아니거나 없으면 미리 받지 않는다`() = runTest {
        store(savedFaceKey = "default:FOX").warm()
        store(savedFaceKey = null).warm()
        runCurrent()
        assertEquals(emptyList(), repo.base.calls.filter { it.startsWith("head:") })
    }

    @Test
    fun `checkSelfie는 기기 작업을 직접 확인하고, 로그아웃 상태면 묻지 않는다(D-820)`() = runTest {
        val store = store()
        store.checkSelfie()
        runCurrent()
        assertEquals(1, checks)

        loggedIn.value = false
        runCurrent()
        store.checkSelfie()
        runCurrent()
        assertEquals(1, checks)
    }

    @Test
    fun `warm은 기기에 남은 셀카 작업 따라가기를 잇는다(리뷰 R2-3)`() = runTest {
        val store = store()
        store.warm()
        runCurrent()
        assertEquals(1, resumes)
    }

    @Test
    fun `warm은 따라가기를 이은 뒤 바로 한 번 직접 확인한다(서버에서 들여온 끝난 작업도 워커를 기다리지 않는다)`() = runTest {
        // 재설치·다른 기기 접수처럼 기기 ID 없이 서버 current로 들여온 작업은 앞 화면 확인이 들여오기 전에 끝나 워커만
        // 남았다(D-855). 들여온 직후 같은 흐름에서 확인해야 「얼굴 만드는 중」이 남지 않는다.
        val order = mutableListOf<String>()
        val store = MyAvatarStore(
            avatars = repo,
            myGender = gender,
            heads = headsOf(repo),
            preload = {},
            prepare = { _, _ -> },
            scope = backgroundScope,
            loggedIn = loggedIn,
            resumeSelfie = { order += "resume" },
            checkPending = { order += "check" }
        )
        store.warm()
        runCurrent()
        assertEquals(listOf("resume", "check"), order)
    }

    @Test
    fun `입력 읽기가 예외를 던져도 앱이 죽지 않고 Failed다(리뷰 R2-8)`() = runTest {
        val store = MyAvatarStore(
            avatars = object : AvatarRepository by repo {
                override suspend fun runner(): Outcome<AvatarRunner> = throw IllegalStateException("boom")
            },
            myGender = gender,
            heads = headsOf(repo),
            preload = {},
            prepare = { _, _ -> },
            scope = backgroundScope,
            loggedIn = loggedIn
        )
        store.warm()
        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Failed>(store.state.value)
    }

    @Test
    fun `셀카 결과를 워커가 적용하면 화면이 없어도 다시 준비하고, 앞서 끝난 성공으로는 다시 읽지 않는다(DR-13)`() = runTest {
        repo.base.progress.value = SelfieProgress.Succeeded("old", "head:7:old")
        val store = store()
        store.warm()
        gender.value = Gender.MALE
        runCurrent()
        val before = repo.avatarCalls

        repo.base.progress.value = SelfieProgress.Working("job-1")
        runCurrent()
        assertEquals(before, repo.avatarCalls, "만드는 중은 다시 읽지 않는다")

        repo.base.progress.value = SelfieProgress.Succeeded("job-1", "head:7:job-1")
        runCurrent()
        assertEquals(before + 1, repo.avatarCalls)

        store.clear()
        repo.base.progress.value = SelfieProgress.Succeeded("job-2", "head:7:job-2")
        runCurrent()
        assertEquals(before + 1, repo.avatarCalls, "로그아웃 뒤에는 듣지 않는다")
    }

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
    fun `성별을 알면 riv를 미리 읽고 세 입력을 모아 서기 모습을 미리 준비한 뒤 Ready이고 이어서 달리기도 준비한다(D-813)`() = runTest {
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
        assertEquals(
            listOf(look.rive to AvatarMotion.STAND, look.rive to AvatarMotion.RUN),
            prepared,
            "칸에 넘길 것과 같은 모습을 서기 먼저, 달리기 다음으로 미리 준비한다"
        )
        prepared.forEach { assertSame(look.rive, it.first) }
    }

    @Test
    fun `달리기 동작 준비는 Ready를 늦추지 않는다(서기만 끝나면 Ready, D-813)`() = runTest {
        val running = CompletableDeferred<Unit>()
        val store = MyAvatarStore(
            avatars = repo,
            myGender = gender,
            heads = headsOf(repo),
            preload = {},
            prepare = { _, motion -> if (motion == AvatarMotion.RUN) running.await() },
            scope = backgroundScope,
            loggedIn = loggedIn
        )
        store.warm()
        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Ready>(store.state.value, "달리기 준비가 끝나기 전에도 Ready다")
        running.complete(Unit)
        runCurrent()
        assertIs<MyAvatarState.Ready>(store.state.value)
    }

    @Test
    fun `달리기 동작 준비가 실패해도 Ready를 지킨다`() = runTest {
        val store = MyAvatarStore(
            avatars = repo,
            myGender = gender,
            heads = headsOf(repo),
            preload = {},
            prepare = { _, motion -> if (motion == AvatarMotion.RUN) throw IOException("run") },
            scope = backgroundScope,
            loggedIn = loggedIn
        )
        store.warm()
        gender.value = Gender.MALE
        runCurrent()
        assertIs<MyAvatarState.Ready>(store.state.value)
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

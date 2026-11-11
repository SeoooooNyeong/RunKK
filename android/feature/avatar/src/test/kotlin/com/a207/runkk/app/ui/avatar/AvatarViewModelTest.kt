package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.BackgroundChoice
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieFailure
import com.a207.runkk.app.domain.avatar.SelfieHead
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.SelfieJobFollower
import com.a207.runkk.app.domain.avatar.SelfieProgress
import com.a207.runkk.app.domain.avatar.SelfieScreenPresence
import com.a207.runkk.app.domain.avatar.Shop
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

val avatar = Avatar(
    faceKey = null,
    defaultCharacterId = "FOX",
    bodyShape = "STAGE3",
    skinTone = "MORNING",
    equipped = mapOf(ItemSlot.TOP to 1L),
    balance = 1_240,
    canCreateSelfieToday = true,
    rejected = emptyMap()
)

val items = listOf(
    Shop.Item(1, ItemSlot.TOP, "라임 러닝 티", 300, isOwned = true, isOnSale = true),
    Shop.Item(2, ItemSlot.TOP, "라벤더 싱글렛", 400, isOwned = true, isOnSale = true),
    Shop.Item(3, ItemSlot.HAT, "런 캡", 250, isOwned = false, isOnSale = true),
    Shop.Item(4, ItemSlot.SHOES, "카본 러닝화", 2_000, isOwned = false, isOnSale = true),
    Shop.Item(5, ItemSlot.BOTTOM, "한정 쇼츠", 500, isOwned = false, isOnSale = false)
)

class FakeAvatars : AvatarRepository {
    val calls = mutableListOf<String>()
    var avatarResult: Outcome<Avatar> = Outcome.Success(avatar)

    /** 셀카 접수 응답(기본: 접수되어 대기 중). */
    var submitResult: Outcome<SelfieJob> = Outcome.Success(SelfieJob("job-1", SelfieJob.Status.QUEUED))

    /** 상태 조회 응답을 차례로 돌려준다. 마지막 것은 되풀이한다. */
    var jobResults: MutableList<Outcome<SelfieJob>> =
        mutableListOf(Outcome.Success(SelfieJob("job-1", SelfieJob.Status.SUCCEEDED)))
    var finishResult: Outcome<Avatar> = Outcome.Success(avatar.copy(faceKey = "head:7:job-1"))

    /** 기기에 남긴 진행 중 작업. */
    var pendingJob: String? = null
    var equipResult: ((Map<ItemSlot, Long?>) -> Outcome<Avatar>) = { changes ->
        Outcome.Success(
            avatar.copy(
                equipped = (avatar.equipped + changes).filterValues {
                    it != null
                }.mapValues { it.value!! }
            )
        )
    }
    var purchaseResult: Outcome<Int> = Outcome.Success(990)
    var lastSelfie: ByteArray? = null

    override suspend fun avatar() = avatarResult

    var runnerResult: Outcome<AvatarRunner> =
        Outcome.Success(AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4))

    override suspend fun runner(): Outcome<AvatarRunner> {
        calls += "runner"
        return runnerResult
    }

    override suspend fun submitSelfie(jpeg: ByteArray): Outcome<SelfieJob> {
        calls += "selfie"
        lastSelfie = jpeg
        return submitResult
    }

    override suspend fun selfieJob(jobId: String): Outcome<SelfieJob> {
        calls += "job:$jobId"
        return if (jobResults.size > 1) jobResults.removeAt(0) else jobResults.first()
    }

    override suspend fun finishSelfie(jobId: String): Outcome<Avatar> {
        calls += "finish:$jobId"
        pendingJob = null
        return finishResult
    }

    override suspend fun pendingSelfieJobId(): String? = pendingJob

    /** 기기에 남긴 마지막 셀카 실패(DV-성-10). */
    var lastFailure: SelfieFailure? = null

    override suspend fun lastSelfieFailure(): SelfieFailure? = lastFailure

    override suspend fun chooseDefault(characterId: String): Outcome<Avatar> {
        calls += "default:$characterId"
        return Outcome.Success(avatar.copy(defaultCharacterId = characterId))
    }

    override suspend fun equip(changes: Map<ItemSlot, Long?>): Outcome<Avatar> {
        calls += "equip:$changes"
        return equipResult(changes)
    }

    /** 상점 읽기 실패(null이면 성공, 잔액 1,240). */
    var shopFailure: AppError? = null

    override suspend fun shop(slot: ItemSlot?): Outcome<Shop> {
        calls += "shop:$slot"
        shopFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(Shop(items.filter { slot == null || it.slot == slot }, 1_240))
    }

    var backgroundsResult: Outcome<AvatarBackgrounds> = Outcome.Success(sampleBackgrounds)
    var chooseResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun backgrounds(): Outcome<AvatarBackgrounds> {
        calls += "backgrounds"
        return backgroundsResult
    }

    override suspend fun chooseBackground(key: String?): Outcome<Unit> {
        calls += "chooseBackground:$key"
        return chooseResult
    }

    override suspend fun backgroundChoice(): Outcome<BackgroundChoice?> = error("unused")

    override suspend fun purchase(itemId: Long): Outcome<Int> {
        calls += "buy:$itemId"
        return purchaseResult
    }

    /** 셀카 얼굴 부위 그림 응답. */
    var faceResult: Outcome<List<FaceLayer>> = Outcome.Success(faceLayers)

    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> {
        calls += "face:$faceKey"
        return faceResult
    }

    /** 셀카 머리 응답(기본: 셀카 머리가 아님). */
    var headResult: Outcome<SelfieHead?> = Outcome.Success(null)

    override suspend fun selfieHead(faceKey: String?): Outcome<SelfieHead?> {
        calls += "head:$faceKey"
        return headResult
    }

    /** 워커가 고치는 셀카 진행(design-oo 05-30 6.1). 시험이 값을 바꿔 워커를 흉내 낸다. */
    val progress = MutableStateFlow<SelfieProgress>(SelfieProgress.None)

    override fun selfieProgress(): Flow<SelfieProgress> = progress

    override fun dismissSelfieResult() {
        calls += "dismiss"
        progress.value = SelfieProgress.None
    }
}

/** 따라가기 기록. */
internal class FakeFollower : SelfieJobFollower {
    val calls = mutableListOf<String>()

    override fun follow(jobId: String) {
        calls += "follow:$jobId"
    }

    override fun resume(jobId: String) {
        calls += "resume:$jobId"
    }

    override fun cancel() {
        calls += "cancel"
    }
}

/** 보낸 사진 한 장(메모리). */
internal class FakePhoto(var saved: ByteArray? = null) : PendingSelfiePhoto {
    override suspend fun save(jpeg: ByteArray) {
        saved = jpeg
    }

    override suspend fun load(): ByteArray? = saved

    override suspend fun clear() {
        saved = null
    }
}

/** 얼굴 만들기 화면 표시(열린 수). */
internal class FakePresence : SelfieScreenPresence {
    var open = 0

    override val isVisible: Boolean get() = open > 0

    override fun opened() {
        open++
    }

    override fun closed() {
        open--
    }
}

/** 시험용 셀카 머리 캐시: 머리 레이어를 바로(같은 스레드에서) 만든다. 화면마다 새로 두면 시험끼리 섞이지 않는다. */
internal fun headsOf(repo: AvatarRepository): SelfieHeads = SelfieHeads(repo, Dispatchers.Unconfined)

/** 얼굴 변환 전송 동의(셀카 카드의 체크). */
internal class FakeConsent(private var given: Boolean?, private val result: Outcome<Unit> = Outcome.Success(Unit)) :
    FaceConsent {
    var gives = 0

    override suspend fun isGiven(): Boolean? = given

    override suspend fun give(): Outcome<Unit> {
        gives++
        if (result is Outcome.Success) given = true
        return result
    }
}

/** 1×1 PNG(부위 그림 표본). */
val tinyPng: ByteArray = java.util.Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

val faceLayers: List<FaceLayer> = FacePart.entries.map { FaceLayer(it, tinyPng) }

/** 시험용 내 아바타 저장소: 성별을 [gender]로 두고 미리 읽기·합성은 하지 않는다. 성별이 null이면 refresh가 아무것도 하지 않는다. */
internal fun storeOf(repo: AvatarRepository, gender: Gender? = null): MyAvatarStore = MyAvatarStore(
    repo,
    flowOf(gender),
    headsOf(repo),
    preload = {},
    prepare = { _, _ -> },
    scope = CoroutineScope(Dispatchers.Unconfined)
)

/** 얼굴 만들기(design-oo 05-30 6.2 상태도, 피그마 AV-7 1안): 고르기 → 사진 확인 → 보냄 → 만드는 중 → 새 얼굴 / 실패. */
class AvatarSetupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeAvatars()
    private val follower = FakeFollower()
    private val photo = FakePhoto()
    private val presence = FakePresence()

    private fun vm(consent: FaceConsent = FakeConsent(true)) =
        AvatarSetupViewModel(repo, follower, photo, presence, headsOf(repo), Dispatchers.Unconfined, consent)

    @Test
    fun `지금 얼굴과 오늘 셀카 가능 여부를 읽고 고르기 단계로 연다`() {
        val s = vm().state.value
        assertEquals(SetupPhase.CHOOSE, s.phase)
        assertTrue(s.canCreateSelfie)
        assertTrue(s.isDefaultSelected, "지금 얼굴이 기본 캐릭터(어느 값이든)면 「기본 얼굴」 칸을 고른 채 연다(D-성-187)")
    }

    @Test
    fun `지금 얼굴이 셀카면 기본 얼굴 칸을 고르지 않고 미리보기용 셀카 층을 읽는다`() {
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1", defaultCharacterId = null))
        val s = vm().state.value
        assertFalse(s.isDefaultSelected)
        assertEquals("selfie:face/7/s1", s.faceKey)
        assertTrue(s.faceLayers.isNotEmpty(), "지금 얼굴 카드가 셀카 머리를 그린다(빈 칸·기본 머리가 아니게)")
    }

    @Test
    fun `RC-3 카메라를 사용자가 취소하면 오류 문구 없이 고르기 단계에 남는다`() {
        val model = vm()
        model.onSelfieUnavailable()
        assertTrue(model.onSelfieRequested())
        model.onSelfieCancelled()
        assertNull(model.state.value.error, "취소는 조용히")
        assertEquals(SetupPhase.CHOOSE, model.state.value.phase)
    }

    @Test
    fun `사진을 고르면 보내지 않고 사진 확인에 크게 보인다`() {
        val model = vm()
        val jpeg = byteArrayOf(1, 2, 3)
        model.onSelfie(jpeg)
        assertEquals(SetupPhase.CONFIRM, model.state.value.phase)
        assertTrue(jpeg.contentEquals(model.state.value.photo))
        assertTrue(repo.calls.none { it == "selfie" })
    }

    @Test
    fun `만들기 시작 - 접수되면 사진을 남기고 워커에 따라가기를 맡겨 만드는 중이다(D-성-117, D-성-119)`() {
        val model = vm()
        val jpeg = byteArrayOf(9)
        model.onSelfie(jpeg)
        model.onStart()
        assertEquals(SetupPhase.WORKING, model.state.value.phase)
        assertTrue(jpeg.contentEquals(photo.saved))
        assertEquals(listOf("follow:job-1"), follower.calls)
        assertTrue(repo.calls.none { it.startsWith("job:") }, "화면은 조회하지 않는다")
    }

    @Test
    fun `만들기 시작 - 동의하지 않았으면 보내지 않고 체크를 요구하며, 체크하면 바로 동의를 저장한다`() {
        val consent = FakeConsent(false)
        val model = vm(consent)
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        assertEquals(UiText.Res(R.string.avatar_setup_face_consent_needed), model.state.value.error)
        assertEquals(SetupPhase.CONFIRM, model.state.value.phase)
        model.onFaceConsent()
        assertEquals(1, consent.gives)
        assertTrue(model.state.value.hasFaceConsent)
        model.onStart()
        assertEquals(SetupPhase.WORKING, model.state.value.phase)
    }

    @Test
    fun `워커가 낸 진행을 단계로 바꾼다 - 성공이면 새 얼굴, 실패면 사유(6_2)`() {
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "head:7:job-1"))
        repo.progress.value = SelfieProgress.Succeeded("job-1", "head:7:job-1")
        assertEquals(SetupPhase.DONE, model.state.value.phase)
        assertEquals("head:7:job-1", model.state.value.faceKey)
        assertNotNull(model.state.value.look, "새 얼굴 무대를 그린다")

        val other = vm()
        repo.progress.value = SelfieProgress.Failed("job-2", "SELFIE_QUALITY_REJECTED")
        assertEquals(SetupPhase.FAILED, other.state.value.phase)
        assertEquals(UiText.Res(R.string.avatar_error_no_face), other.state.value.failure)
    }

    @Test
    fun `상한 초과 실패는 시간 초과 문구다`() {
        val model = vm()
        repo.progress.value = SelfieProgress.Failed("job-1", SelfieProgress.TIMEOUT)
        assertEquals(UiText.Res(R.string.avatar_error_selfie_timeout), model.state.value.failure)
    }

    @Test
    fun `다시 들어오면 만드는 중을 이어 보이고 남긴 사진을 보이며 남은 작업 따라가기를 잇는다`() {
        photo.saved = byteArrayOf(7)
        repo.pendingJob = "job-1"
        repo.progress.value = SelfieProgress.Working("job-1", 1_000L)
        val model = vm()
        assertEquals(SetupPhase.WORKING, model.state.value.phase)
        assertTrue(byteArrayOf(7).contentEquals(model.state.value.photo))
        assertEquals(listOf("resume:job-1"), follower.calls)
    }

    @Test
    fun `사진 확인 중에는 다른 작업 진행이 단계를 바꾸지 않는다`() {
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        repo.progress.value = SelfieProgress.Failed("old", null)
        assertEquals(SetupPhase.CONFIRM, model.state.value.phase)
    }

    @Test
    fun `서버에 작업이 없다고 확인돼 진행이 없어지면 고르기로 돌아간다(회수)`() {
        repo.progress.value = SelfieProgress.Working("job-1")
        val model = vm()
        repo.progress.value = SelfieProgress.None
        assertEquals(SetupPhase.CHOOSE, model.state.value.phase)
        assertNull(model.state.value.photo)
    }

    @Test
    fun `새 얼굴 확인은 결과를 본 것으로 두고 돌아간다`() = runTest(main.dispatcher) {
        repo.progress.value = SelfieProgress.Succeeded("job-1", "head:7:job-1")
        val model = vm()
        model.onDoneConfirm()
        assertTrue("dismiss" in repo.calls)
        assertEquals(AvatarSetupEvent.Done, model.events.first())
    }

    @Test
    fun `실패에서 다른 사진 고르기는 실패를 지우고 고르기로 간다(실패는 횟수에 세지 않는다)`() {
        repo.progress.value = SelfieProgress.Failed("job-1", null)
        val model = vm()
        model.onRetry()
        assertEquals(SetupPhase.CHOOSE, model.state.value.phase)
        assertTrue(model.state.value.canCreateSelfie)
        assertTrue("dismiss" in repo.calls)
    }

    @Test
    fun `보내지 못하면 고르기로 돌아가 사유를 보인다(6_2 Sending → Choose)`() {
        repo.submitResult = Outcome.Failure(AppError.Server(503))
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        assertEquals(SetupPhase.CHOOSE, model.state.value.phase)
        assertEquals(UiText.Res(R.string.avatar_error_upstream_failed), model.state.value.error)
        assertNull(model.state.value.photo)
        assertTrue(follower.calls.isEmpty())
    }

    @Test
    fun `하루 1회를 넘기면 안내하고 셀카를 막는다(FR-157)`() {
        repo.submitResult = Outcome.Failure(AppError.Rejected("SELFIE_LIMIT"))
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        assertFalse(model.state.value.canCreateSelfie)
        assertFalse(model.onSelfieRequested())
        assertEquals(UiText.Res(R.string.avatar_error_selfie_limit), model.state.value.error)
    }

    @Test
    fun `서버가 동의 없음으로 거부하면(다른 곳에서 철회) 체크를 풀고 체크를 요구한다`() {
        repo.submitResult = Outcome.Failure(AppError.Rejected("FACE_CONSENT_REQUIRED"))
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        assertFalse(model.state.value.hasFaceConsent)
        assertEquals(UiText.Res(R.string.avatar_setup_face_consent_needed), model.state.value.error)
    }

    @Test
    fun `접수 응답이 이미 실패면 실패 화면이고 따라가지 않는다`() {
        repo.submitResult = Outcome.Success(SelfieJob("job-1", SelfieJob.Status.FAILED, "NO_FACE_DETECTED"))
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        assertEquals(SetupPhase.FAILED, model.state.value.phase)
        assertTrue(follower.calls.isEmpty())
    }

    @Test
    fun `기본 얼굴에서 셀카가 만들어지면 기본 얼굴 칸 고름을 풀어 새 셀카를 기본 얼굴로 덮지 않는다(D-성-187, 리뷰 U-11)`() {
        val model = vm()
        assertTrue(model.state.value.isDefaultSelected)
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        model.onDefaultFaceSelected()
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "head:7:job-1", defaultCharacterId = null))
        repo.progress.value = SelfieProgress.Succeeded("job-1", "head:7:job-1")
        assertEquals("head:7:job-1", model.state.value.faceKey)
        assertFalse(model.state.value.isDefaultSelected, "새 셀카가 지금 얼굴이다(카드가 셀카를 그리고 저장 단추가 없다)")
    }

    @Test
    fun `만드는 중에도 기본 얼굴을 저장할 수 있고(서버에는 RABBIT) 따라가기를 멈추고 사진을 지운 뒤 돌아간다(FR-158, D-성-187)`() = runTest(main.dispatcher) {
        val model = vm()
        model.onSelfie(byteArrayOf(1))
        model.onStart()
        assertFalse(model.onSelfieRequested(), "만드는 중에는 셀카를 막는다")
        model.onDefaultFaceSelected()
        assertTrue(model.state.value.isDefaultSelected)
        model.onConfirmDefaultFace()
        assertEquals(listOf("default:RABBIT"), repo.calls.filter { it.startsWith("default:") })
        assertTrue("cancel" in follower.calls)
        assertNull(photo.saved)
        assertEquals(AvatarSetupEvent.Done, model.events.first())
    }

    @Test
    fun `화면이 보이는 동안 얼굴 만들기 화면 표시를 열고 안 보이면 닫는다(D-성-120)`() {
        val model = vm()
        model.onScreenVisible()
        assertTrue(presence.isVisible)
        model.onScreenHidden()
        assertFalse(presence.isVisible)
    }
}

/** 꾸미기(피그마 제안 A안): 칸을 누르면 무대에만 입혀 보고, 저장이 바뀐 부위만 보낸다. */
class AvatarDecorateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeAvatars()

    private fun vm(source: AvatarRepository = repo) = AvatarViewModel(source, headsOf(source), storeOf(source))

    @Test
    fun `부위 칸은 가진 아이템과 잠긴 아이템이고 잠긴 칸은 해금 조건을 안내한다`() {
        val locked =
            Shop.Item(6, ItemSlot.TOP, "완주 기념 맨투맨", 300, false, true, isLocked = true, unlockLabel = "하프 완주하면 열려요")
        val source = object : AvatarRepository by repo {
            override suspend fun shop(slot: ItemSlot?) = Outcome.Success(Shop(items + locked, 1_240))
        }
        val model = vm(source)
        assertEquals(listOf(1L, 2L, 6L), model.state.value.cellsInSlot.map { it.itemId })
        model.onSlot(ItemSlot.HAT)
        assertTrue(model.state.value.cellsInSlot.isEmpty())
        model.onSlot(ItemSlot.TOP)
        model.onToggle(locked)
        assertEquals(UiText.Plain("하프 완주하면 열려요"), model.state.value.message)
    }

    @Test
    fun `칸을 누르면 저장 전에 무대에 바로 입혀 보고 되돌리기는 들어올 때로 돌린다`() {
        val model = vm()
        model.onToggle(items[1])
        assertEquals(2L, model.state.value.draft[ItemSlot.TOP])
        assertTrue(model.state.value.isDirty)
        assertTrue(repo.calls.none { it.startsWith("equip") }, "저장 전에는 보내지 않는다")
        model.onToggle(items[1])
        assertNull(model.state.value.draft[ItemSlot.TOP])
        model.onUndo()
        assertEquals(1L, model.state.value.draft[ItemSlot.TOP])
        assertFalse(model.state.value.isDirty)
    }

    @Test
    fun `저장은 바뀐 부위만 한 번에 보내고 홈 칸을 다시 준비한 뒤 닫는다(FR-161, D-성-104)`() {
        val store = storeOf(repo, Gender.MALE).also { it.warm() }
        val model = AvatarViewModel(repo, headsOf(repo), store)
        val before = repo.calls.count { it == "runner" }
        model.onToggle(items[1])
        model.onSave()
        assertEquals(listOf("equip:{TOP=2}"), repo.calls.filter { it.startsWith("equip") })
        assertTrue(model.state.value.isDone)
        assertEquals(2L, model.state.value.avatar?.equipped?.get(ItemSlot.TOP))
        assertTrue(repo.calls.count { it == "runner" } > before, "저장 뒤 MyAvatarStore가 다시 읽는다")
    }

    @Test
    fun `숨긴 모자 부위의 착용은 저장해도 그대로 둔다(보내지 않는다, D-성-183)`() {
        repo.avatarResult = Outcome.Success(avatar.copy(equipped = mapOf(ItemSlot.TOP to 1L, ItemSlot.HAT to 3L)))
        val model = AvatarViewModel(repo, headsOf(repo), storeOf(repo))
        model.onToggle(items[1])
        model.onSave()
        assertEquals(listOf("equip:{TOP=2}"), repo.calls.filter { it.startsWith("equip") })
    }

    @Test
    fun `거부된 저장은 홈 칸을 다시 준비하지 않는다(리뷰 R2-6)`() {
        repo.equipResult = { Outcome.Success(avatar.copy(rejected = mapOf(ItemSlot.TOP to "ITEM_NOT_OWNED"))) }
        val store = storeOf(repo, Gender.MALE).also { it.warm() }
        val model = AvatarViewModel(repo, headsOf(repo), store)
        val before = repo.calls.count { it == "runner" }
        model.onToggle(items[1])
        model.onSave()
        assertEquals(before, repo.calls.count { it == "runner" })
    }

    @Test
    fun `상점에서 돌아와 다시 읽을 때 로딩 표시로 깜박이지 않는다(리뷰 R2-15)`() {
        val model = vm()
        val seen = mutableListOf<Boolean>()
        val watcher = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch {
            model.state.collect { seen += it.isLoading }
        }
        model.onResumed()
        model.onResumed()
        watcher.cancel()
        assertTrue(seen.none { it }, "이미 그린 화면은 다시 읽는 동안 로딩으로 바꾸지 않는다: $seen")
    }

    @Test
    fun `바꾼 것이 없으면 보내지 않고 닫는다`() {
        val model = vm()
        model.onSave()
        assertTrue(repo.calls.none { it.startsWith("equip") })
        assertTrue(model.state.value.isDone)
    }

    @Test
    fun `거부된 착용은 사유를 안내하고 화면을 둔다`() {
        repo.equipResult = { Outcome.Success(avatar.copy(rejected = mapOf(ItemSlot.TOP to "ITEM_NOT_OWNED"))) }
        val model = vm()
        model.onToggle(items[1])
        model.onSave()
        assertEquals(
            uiText(
                R.string.avatar_equip_rejected,
                UiText.Res(R.string.avatar_slot_top),
                UiText.Res(R.string.avatar_error_item_not_owned)
            ),
            model.state.value.message
        )
        assertFalse(model.state.value.isDone)
    }

    @Test
    fun `셀카 결과 적용은 워커가 하므로 꾸미기는 결과를 받지 않는다(D-성-118)`() {
        repo.pendingJob = "job-1"
        vm()
        assertTrue(repo.calls.none { it.startsWith("finish:") || it.startsWith("job:") })
    }

    @Test
    fun `불러오지 못하면 오류`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        assertNotNull(vm().state.value.error)
    }

    @Test
    fun `셀카 얼굴이면 부위 그림을 받고 같은 얼굴은 다시 받지 않는다(FR-155)`() {
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1"))
        val model = vm()
        assertEquals(faceLayers, model.state.value.faceLayers)
        model.onResumed()
        model.onResumed()
        assertEquals(1, repo.calls.count { it == "face:selfie:face/7/s1" })
    }

    @Test
    fun `기본 캐릭터면 받지 않고, 받지 못하면 비워 자리 그림을 그린다`() {
        val plain = vm()
        assertEquals(emptyList(), plain.state.value.faceLayers)
        assertTrue(repo.calls.none { it.startsWith("face:") })
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1"))
        repo.faceResult = Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND"))
        val failed = vm()
        assertEquals(emptyList(), failed.state.value.faceLayers)
        assertNull(failed.state.value.error, "얼굴 그림 실패는 화면 오류가 아니다")
    }
}

/** 상점(피그마 제안 A안): 칸을 골라 입어 보고, 확인 뒤 사고, 가진 것은 입는다. */
class ShopViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeAvatars()

    private fun vm() = ShopViewModel(repo, headsOf(repo), storeOf(repo))

    @Test
    fun `부위로 거르고 칸을 고르면 무대에 입혀 보며 확인 뒤 사면 잔액과 보유를 바꾸고 고른 채 둔다(FR-160)`() {
        val model = vm()
        model.onSlot(ItemSlot.HAT)
        assertEquals(listOf(3L), model.state.value.itemsInSlot.map { it.itemId })
        model.onPick(items[2])
        assertEquals(3L, model.state.value.tryOn[ItemSlot.HAT])
        model.onBuyRequest()
        assertEquals(items[2], model.state.value.confirming)
        model.onBuy()
        val s = model.state.value
        assertEquals(990, s.shop?.balance)
        assertTrue(s.itemsInSlot.first().isOwned)
        assertNull(s.confirming)
        assertEquals(3L, s.selected?.itemId)
        assertTrue(s.selected!!.isOwned, "산 칸은 바로 입을 수 있다")
        assertTrue("buy:3" in repo.calls)
    }

    @Test
    fun `가진 칸을 고르면 입기로 바로 입고 무대의 입어 보기를 푼다`() {
        val model = vm()
        model.onPick(items[1])
        model.onBuyRequest()
        assertNull(model.state.value.confirming, "가진 것은 사지 않는다")
        model.onEquip()
        assertEquals(listOf("equip:{TOP=2}"), repo.calls.filter { it.startsWith("equip") })
        assertEquals(2L, model.state.value.avatar?.equipped?.get(ItemSlot.TOP))
        assertNull(model.state.value.selected)
        assertEquals(
            uiText(R.string.avatar_shop_equipped, items[1].name, objectParticle(items[1].name)),
            model.state.value.message
        )
    }

    @Test
    fun `입기가 거부되면 홈 칸을 다시 준비하지 않는다(리뷰 R2-6)`() {
        repo.equipResult = { Outcome.Success(avatar.copy(rejected = mapOf(ItemSlot.TOP to "ITEM_NOT_OWNED"))) }
        val store = storeOf(repo, Gender.MALE).also { it.warm() }
        val model = ShopViewModel(repo, headsOf(repo), store)
        val before = repo.calls.count { it == "runner" }
        model.onPick(items[1])
        model.onEquip()
        assertEquals(before, repo.calls.count { it == "runner" })
    }

    @Test
    fun `입기가 되면 홈 칸을 다시 준비한다`() {
        val store = storeOf(repo, Gender.MALE).also { it.warm() }
        val model = ShopViewModel(repo, headsOf(repo), store)
        val before = repo.calls.count { it == "runner" }
        model.onPick(items[1])
        model.onEquip()
        assertTrue(repo.calls.count { it == "runner" } > before)
    }

    @Test
    fun `벗기기는 입어 보던 칸을 풀어 지금 착용으로 돌린다`() {
        val model = vm()
        model.onPick(items[3])
        assertEquals(4L, model.state.value.tryOn[ItemSlot.SHOES])
        model.onClearTryOn()
        assertNull(model.state.value.tryOn[ItemSlot.SHOES])
        assertEquals(1L, model.state.value.tryOn[ItemSlot.TOP])
    }

    @Test
    fun `잠긴 칸은 고르지 않고 해금 조건을 안내하며 판매 종료이고 가지지 않은 칸은 고르지 않는다`() {
        val locked =
            Shop.Item(6, ItemSlot.TOP, "완주 기념 맨투맨", 300, false, true, isLocked = true, unlockLabel = "하프 완주하면 열려요")
        val model = vm()
        model.onPick(locked)
        assertNull(model.state.value.selected)
        assertEquals(UiText.Plain("하프 완주하면 열려요"), model.state.value.message)
        model.onPick(items[4])
        assertNull(model.state.value.selected)
    }

    @Test
    fun `잔액보다 비싼 아이템은 확인 창 없이 바로 리워드 부족을 안내한다`() {
        val model = vm()
        // 카본 러닝화 2,000 P > 잔액 1,240 P
        model.onPick(items[3])
        model.onBuyRequest()
        assertNull(model.state.value.confirming)
        assertEquals(UiText.Res(R.string.avatar_error_insufficient_reward), model.state.value.message)
        model.onBuy()
        assertFalse("buy:4" in repo.calls)
    }

    @Test
    fun `잔액을 모르면 확인 뒤 서버의 잔액 부족 거절을 안내한다`() {
        repo.shopFailure = AppError.Network
        val model = vm()
        assertNull(model.state.value.shop)
        repo.purchaseResult = Outcome.Failure(AppError.Rejected("INSUFFICIENT_REWARD"))
        model.onPick(items[3])
        model.onBuyRequest()
        assertEquals(items[3], model.state.value.confirming)
        model.onBuy()
        assertTrue("buy:4" in repo.calls)
        assertEquals(UiText.Res(R.string.avatar_error_insufficient_reward), model.state.value.message)
        assertFalse(model.state.value.isBuying)
    }
}

package com.a207.runkk.app.ui.avatar

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.BackgroundChoice
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.SelfieFailure
import com.a207.runkk.app.domain.avatar.SelfieHead
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.Shop
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

class AvatarSetupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeAvatars()

    @Test
    fun `지금 얼굴과 오늘 셀카 가능 여부를 읽는다`() {
        val vm = AvatarSetupViewModel(repo)
        assertEquals(DefaultCharacter.FOX, vm.state.value.selected)
        assertTrue(vm.state.value.canCreateSelfie)
    }

    @Test
    fun `셀카를 접수하면 만드는 중을 보이고 간격을 두고 조회해 끝나면 결과를 받아 돌아간다(FR-155)`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.RUNNING)),
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.SUCCEEDED))
        )
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L, 2_000L))
        vm.events.test {
            vm.onSelfie(byteArrayOf(1, 2, 3))
            assertTrue(vm.state.value.isMakingFace)
            assertFalse(vm.state.value.isWorking)
            assertTrue(repo.calls.none { it.startsWith("job:") }, "접수 직후에는 바로 조회하지 않는다")
            advanceTimeBy(1_001)
            assertEquals(1, repo.calls.count { it == "job:job-1" })
            assertTrue(vm.state.value.isMakingFace)
            advanceTimeBy(2_001)
            assertEquals(AvatarSetupEvent.Done, awaitItem())
        }
        assertEquals(listOf("selfie", "job:job-1", "job:job-1", "finish:job-1"), repo.calls)
        assertFalse(vm.state.value.isMakingFace)
        assertEquals("head:7:job-1", vm.state.value.faceKey)
        assertEquals(3, repo.lastSelfie?.size)
    }

    @Test
    fun `얼굴을 만들지 못하면 사유 문구를 보이고 다시 찍을 수 있다`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.FAILED, "SELFIE_QUALITY_REJECTED"))
        )
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.onSelfie(byteArrayOf(1))
        advanceTimeBy(1_001)
        assertFalse(vm.state.value.isMakingFace)
        assertEquals(UiText.Res(R.string.avatar_error_no_face), vm.state.value.error)
        assertTrue(vm.state.value.canCreateSelfie)
        assertTrue(repo.calls.none { it.startsWith("finish:") })
    }

    @Test
    fun `얼굴을 만들지 못하면 다시 시도할 수 있고, 다시 시도는 동의를 확인한 뒤 사진을 고르게 한다(DV-성-10)`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.FAILED, "GENERATION_OUTCOME_UNKNOWN"))
        )
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        assertFalse(vm.state.value.canRetrySelfie)
        vm.onSelfie(byteArrayOf(1))
        advanceTimeBy(1_001)
        assertTrue(vm.state.value.canRetrySelfie)
        assertEquals(UiText.Res(R.string.avatar_error_selfie_failed), vm.state.value.error)
        assertTrue(vm.onSelfieRequested())
        repo.jobResults = mutableListOf(Outcome.Success(SelfieJob("job-1", SelfieJob.Status.RUNNING)))
        vm.onSelfie(byteArrayOf(1))
        assertFalse(vm.state.value.canRetrySelfie, "다시 보내면 실패 표시를 지운다")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `나갔다 다시 들어와도 남긴 실패를 셀카 카드에 보이고 서버에 한 번 다시 묻는다(DV-성-10)`() = runTest(main.dispatcher) {
        repo.lastFailure = SelfieFailure("job-1", "SELFIE_QUALITY_REJECTED")
        repo.jobResults = mutableListOf(
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.FAILED, "SELFIE_QUALITY_REJECTED"))
        )
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        assertEquals(UiText.Res(R.string.avatar_error_no_face), vm.state.value.error)
        assertTrue(vm.state.value.canRetrySelfie)
        assertFalse(vm.state.value.isMakingFace)
        assertEquals(listOf("job:job-1"), repo.calls)
    }

    @Test
    fun `다시 들어왔을 때 실패했던 작업을 서버가 회수했으면 결과를 받아 돌아간다(DV-성-9)`() = runTest(main.dispatcher) {
        repo.lastFailure = SelfieFailure("job-1", "GENERATION_OUTCOME_UNKNOWN")
        repo.jobResults = mutableListOf(Outcome.Success(SelfieJob("job-1", SelfieJob.Status.SUCCEEDED)))
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.events.test {
            assertEquals(AvatarSetupEvent.Done, awaitItem())
        }
        assertEquals(listOf("job:job-1", "finish:job-1"), repo.calls)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.canRetrySelfie)
    }

    @Test
    fun `상태 조회는 아바타 서버의 생성 제한 15분보다 오래 이어 간다(DV-성-9)`() {
        assertTrue(
            AvatarSetupViewModel.pollWindowMillis() > AvatarSetupViewModel.AVATAR_GENERATION_LIMIT_MILLIS,
            "조회 기간 ${AvatarSetupViewModel.pollWindowMillis()}ms"
        )
    }

    @Test
    fun `모르는 실패 사유는 공용 문구다`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.FAILED, "FACE_ASSETS_INCOMPLETE"))
        )
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.onSelfie(byteArrayOf(1))
        advanceTimeBy(1_001)
        assertEquals(UiText.Res(R.string.avatar_error_selfie_failed), vm.state.value.error)
    }

    @Test
    fun `다시 들어오면 남은 작업을 바로 이어 조회한다`() = runTest(main.dispatcher) {
        repo.pendingJob = "job-9"
        repo.jobResults = mutableListOf(Outcome.Success(SelfieJob("job-9", SelfieJob.Status.SUCCEEDED)))
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.events.test {
            assertEquals(AvatarSetupEvent.Done, awaitItem())
        }
        assertEquals(listOf("job:job-9", "finish:job-9"), repo.calls)
    }

    @Test
    fun `만드는 중에는 셀카를 막고 기본 캐릭터는 저장할 수 있으며 조회를 멈추고 한 번만 돌아간다`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(Outcome.Success(SelfieJob("job-1", SelfieJob.Status.RUNNING)))
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.onSelfie(byteArrayOf(1))
        vm.onSelfie(byteArrayOf(2))
        assertFalse(vm.onSelfieRequested())
        assertEquals(1, repo.calls.count { it == "selfie" })
        assertTrue(vm.state.value.isMakingFace)
        vm.events.test {
            vm.onCharacterSelected(DefaultCharacter.CAT)
            vm.onConfirmCharacter()
            assertEquals(AvatarSetupEvent.Done, awaitItem())
            advanceTimeBy(10_000)
            expectNoEvents()
        }
        assertTrue("default:CAT" in repo.calls)
        assertFalse(vm.state.value.isMakingFace)
        assertTrue(repo.calls.none { it.startsWith("job:") }, "저장 뒤에는 조회하지 않는다(다음에 들어올 때 이어 끝낸다)")
    }

    @Test
    fun `동의하지 않았으면 셀카를 열지 않고 체크를 먼저 요구하며, 체크하면 바로 동의를 저장한다`() = runTest(main.dispatcher) {
        val consent = FakeConsent(given = false)
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L), consent)
        assertFalse(vm.state.value.hasFaceConsent)
        assertFalse(vm.onSelfieRequested())
        assertEquals(UiText.Res(R.string.avatar_setup_face_consent_needed), vm.state.value.error)
        vm.onFaceConsent()
        assertTrue(vm.state.value.hasFaceConsent)
        assertNull(vm.state.value.error)
        assertEquals(1, consent.gives)
        vm.onFaceConsent()
        assertEquals(1, consent.gives, "이미 동의했으면 다시 저장하지 않는다")
        assertTrue(vm.onSelfieRequested())
    }

    @Test
    fun `이미 동의했으면 체크된 채 시작하고, 동의 저장이 실패하면 안내한다`() = runTest(main.dispatcher) {
        assertTrue(
            AvatarSetupViewModel(
                repo,
                main.dispatcher,
                listOf(1_000L),
                FakeConsent(given = true)
            ).state.value.hasFaceConsent
        )
        val failing = FakeConsent(given = false, result = Outcome.Failure(AppError.Network))
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L), failing)
        vm.onFaceConsent()
        assertFalse(vm.state.value.hasFaceConsent)
        assertFalse(vm.state.value.isSavingConsent)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `조회가 잠깐 실패해도 이어 가고 세 번 이어 실패하면 멈춘다`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(
            Outcome.Failure(AppError.Network),
            Outcome.Success(SelfieJob("job-1", SelfieJob.Status.RUNNING)),
            Outcome.Failure(AppError.Network),
            Outcome.Failure(AppError.Server(503)),
            Outcome.Failure(AppError.Network)
        )
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.onSelfie(byteArrayOf(1))
        advanceTimeBy(4_001)
        assertTrue(vm.state.value.isMakingFace)
        advanceTimeBy(1_000)
        assertFalse(vm.state.value.isMakingFace)
        assertNotNull(vm.state.value.error)
        assertEquals(5, repo.calls.count { it == "job:job-1" })
    }

    @Test
    fun `오래 끝나지 않으면 조회를 멈추고 만드는 중이라고 안내한다`() = runTest(main.dispatcher) {
        repo.jobResults = mutableListOf(Outcome.Success(SelfieJob("job-1", SelfieJob.Status.RUNNING)))
        val vm = AvatarSetupViewModel(repo, main.dispatcher, listOf(1_000L))
        vm.onSelfie(byteArrayOf(1))
        advanceTimeBy(AvatarSetupViewModel.MAX_POLLS * 1_000L + 1)
        assertFalse(vm.state.value.isMakingFace)
        assertEquals(UiText.Res(R.string.avatar_error_selfie_in_progress), vm.state.value.error)
        assertEquals(AvatarSetupViewModel.MAX_POLLS, repo.calls.count { it == "job:job-1" })
    }

    @Test
    fun `하루 1회를 넘기면 안내하고 셀카를 막고 기본 캐릭터는 고를 수 있다(FR-157·FR-158)`() = runTest(main.dispatcher) {
        repo.submitResult = Outcome.Failure(AppError.Rejected("SELFIE_LIMIT"))
        val vm = AvatarSetupViewModel(repo)
        vm.onSelfie(byteArrayOf(1))
        assertFalse(vm.state.value.canCreateSelfie)
        assertFalse(vm.state.value.isMakingFace)
        assertEquals(UiText.Res(R.string.avatar_error_selfie_limit), vm.state.value.error)
        vm.events.test {
            vm.onCharacterSelected(DefaultCharacter.CAT)
            vm.onConfirmCharacter()
            assertEquals(AvatarSetupEvent.Done, awaitItem())
        }
        assertTrue("default:CAT" in repo.calls)
    }

    @Test
    fun `서버가 동의 없음으로 거부하면(다른 곳에서 철회) 체크를 풀고 체크를 요구한다`() {
        repo.submitResult = Outcome.Failure(AppError.Rejected("FACE_CONSENT_REQUIRED"))
        val vm = AvatarSetupViewModel(repo)
        assertTrue(vm.state.value.hasFaceConsent)
        vm.onSelfie(byteArrayOf(1))
        assertEquals(UiText.Res(R.string.avatar_setup_face_consent_needed), vm.state.value.error)
        assertFalse(vm.state.value.hasFaceConsent)
        assertTrue(vm.state.value.canCreateSelfie)
    }

    @Test
    fun `얼굴 서버가 접수하지 못하면 셀카 문구이고 다시 시도할 수 있다`() {
        repo.submitResult = Outcome.Failure(AppError.Server(503))
        val vm = AvatarSetupViewModel(repo)
        vm.onSelfie(byteArrayOf(1))
        assertEquals(UiText.Res(R.string.avatar_error_upstream_failed), vm.state.value.error)
        assertTrue(vm.state.value.canCreateSelfie)
    }

    @Test
    fun `네트워크 실패면 셀카를 다시 시도할 수 있다`() {
        repo.submitResult = Outcome.Failure(AppError.Network)
        val vm = AvatarSetupViewModel(repo)
        vm.onSelfie(byteArrayOf(1))
        assertTrue(vm.state.value.canCreateSelfie)
        assertNotNull(vm.state.value.error)
    }
}

class AvatarDecorateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeAvatars()

    @Test
    fun `아바타·잔액·보유 아이템을 부위별로 보인다(FR-162)`() {
        val vm = AvatarViewModel(repo, headsOf(repo))
        val s = vm.state.value
        assertEquals(1_240, s.avatar?.balance)
        assertEquals(listOf(1L, 2L), s.ownedInSlot.map { it.itemId })
        assertEquals("라임 러닝 티", s.itemName(1))
        vm.onSlot(ItemSlot.HAT)
        assertTrue(vm.state.value.ownedInSlot.isEmpty())
    }

    @Test
    fun `입은 아이템을 누르면 벗기고 다른 아이템은 바꿔 입힌다(FR-161)`() {
        val vm = AvatarViewModel(repo, headsOf(repo))
        vm.onToggle(items[1])
        assertEquals(2L, vm.state.value.avatar?.equipped?.get(ItemSlot.TOP))
        vm.onToggle(items[1])
        assertNull(vm.state.value.avatar?.equipped?.get(ItemSlot.TOP))
        assertEquals(listOf("equip:{TOP=2}", "equip:{TOP=null}"), repo.calls.filter { it.startsWith("equip") })
    }

    @Test
    fun `거부된 착용은 사유를 안내한다`() {
        repo.equipResult = { Outcome.Success(avatar.copy(rejected = mapOf(ItemSlot.TOP to "ITEM_NOT_OWNED"))) }
        val vm = AvatarViewModel(repo, headsOf(repo))
        vm.onToggle(items[1])
        assertEquals(
            uiText(
                R.string.avatar_equip_rejected,
                UiText.Res(R.string.avatar_slot_top),
                UiText.Res(R.string.avatar_error_item_not_owned)
            ),
            vm.state.value.message
        )
    }

    @Test
    fun `얼굴 만들기를 떠난 사이 셀카 얼굴이 다 만들어졌으면 결과를 받아 새 얼굴을 보인다`() {
        repo.pendingJob = "job-1"
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "head:7:job-1"))
        val vm = AvatarViewModel(repo, headsOf(repo))
        assertTrue("finish:job-1" in repo.calls)
        assertEquals("head:7:job-1", vm.state.value.avatar?.faceKey)
    }

    @Test
    fun `아직 만드는 중이면 결과를 받지 않는다`() {
        repo.pendingJob = "job-1"
        repo.jobResults = mutableListOf(Outcome.Success(SelfieJob("job-1", SelfieJob.Status.RUNNING)))
        AvatarViewModel(repo, headsOf(repo))
        assertTrue("job:job-1" in repo.calls)
        assertTrue(repo.calls.none { it.startsWith("finish:") })
    }

    @Test
    fun `불러오지 못하면 오류`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        val vm = AvatarViewModel(repo, headsOf(repo))
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `셀카 얼굴이면 부위 그림을 받고 같은 얼굴은 다시 받지 않는다(FR-155)`() {
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1"))
        val vm = AvatarViewModel(repo, headsOf(repo))
        assertEquals(faceLayers, vm.state.value.faceLayers)
        vm.onResumed()
        vm.onResumed()
        assertEquals(1, repo.calls.count { it == "face:selfie:face/7/s1" })
    }

    @Test
    fun `기본 캐릭터면 받지 않고, 받지 못하면 비워 자리 그림을 그린다`() {
        val plain = AvatarViewModel(repo, headsOf(repo))
        assertEquals(emptyList(), plain.state.value.faceLayers)
        assertTrue(repo.calls.none { it.startsWith("face:") })
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1"))
        repo.faceResult = Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND"))
        val failed = AvatarViewModel(repo, headsOf(repo))
        assertEquals(emptyList(), failed.state.value.faceLayers)
        assertNull(failed.state.value.error, "얼굴 그림 실패는 화면 오류가 아니다")
    }
}

class ShopViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val repo = FakeAvatars()

    @Test
    fun `부위로 거르고, 확인 뒤 사면 잔액과 보유를 바꾼다(FR-160)`() {
        val vm = ShopViewModel(repo)
        vm.onSlot(ItemSlot.HAT)
        assertEquals(listOf(3L), vm.state.value.shop?.items?.map { it.itemId })
        vm.onPick(items[2])
        assertEquals(items[2], vm.state.value.selected)
        vm.onBuyRequest()
        assertEquals(items[2], vm.state.value.confirming)
        vm.onBuy()
        val s = vm.state.value
        assertEquals(990, s.shop?.balance)
        assertTrue(s.shop!!.items.first().isOwned)
        assertNull(s.confirming)
        assertNull(s.selected)
        assertTrue("buy:3" in repo.calls)
    }

    @Test
    fun `보유, 판매 종료 아이템은 고를 수 없고 잔액을 모르면 확인 뒤 서버의 잔액 부족 거절을 안내한다`() {
        repo.shopFailure = AppError.Network
        val vm = ShopViewModel(repo)
        vm.onPick(items[0])
        vm.onPick(items[4])
        assertNull(vm.state.value.selected)
        vm.onBuyRequest()
        assertNull(vm.state.value.confirming)
        assertNull(vm.state.value.shop)
        repo.purchaseResult = Outcome.Failure(AppError.Rejected("INSUFFICIENT_REWARD"))
        vm.onPick(items[3])
        vm.onBuyRequest()
        assertEquals(items[3], vm.state.value.confirming)
        vm.onBuy()
        assertTrue("buy:4" in repo.calls)
        assertEquals(UiText.Res(R.string.avatar_error_insufficient_reward), vm.state.value.message)
        assertFalse(vm.state.value.isBuying)
    }

    @Test
    fun `잔액보다 비싼 아이템을 고르면 확인 창 없이 바로 리워드 부족을 안내한다`() {
        val vm = ShopViewModel(repo)
        // 카본 러닝화 2,000 P > 잔액 1,240 P
        vm.onPick(items[3])
        vm.onBuyRequest()
        assertNull(vm.state.value.confirming)
        assertEquals(UiText.Res(R.string.avatar_error_insufficient_reward), vm.state.value.message)
        vm.onBuy()
        assertFalse("buy:4" in repo.calls)
    }

    @Test
    fun `셀카 접수 거부(사진 형식)는 사유 안내와 기본 캐릭터 선택으로 이어진다`() {
        repo.submitResult = Outcome.Failure(AppError.Rejected("UNSUPPORTED_IMAGE"))
        val vm = AvatarSetupViewModel(repo)
        vm.onSelfie(byteArrayOf(1))
        assertEquals(UiText.Res(R.string.avatar_error_unsupported_image), vm.state.value.error)
        assertFalse(vm.state.value.isWorking)
        assertTrue(vm.state.value.canCreateSelfie)
    }
}

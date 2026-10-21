package com.a207.runkk.app.ui.avatar

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
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
    Shop.Item(1, ItemSlot.TOP, "라임 러닝 티", 300, owned = true, onSale = true),
    Shop.Item(2, ItemSlot.TOP, "라벤더 싱글렛", 400, owned = true, onSale = true),
    Shop.Item(3, ItemSlot.HAT, "런 캡", 250, owned = false, onSale = true),
    Shop.Item(4, ItemSlot.SHOES, "카본 러닝화", 2_000, owned = false, onSale = true),
    Shop.Item(5, ItemSlot.BOTTOM, "한정 쇼츠", 500, owned = false, onSale = false)
)

class FakeAvatars : AvatarRepository {
    val calls = mutableListOf<String>()
    var avatarResult: Outcome<Avatar> = Outcome.Success(avatar)
    var selfieResult: Outcome<Avatar> = Outcome.Success(avatar.copy(faceKey = "face-1"))
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

    override suspend fun createFromSelfie(jpeg: ByteArray): Outcome<Avatar> {
        calls += "selfie"
        lastSelfie = jpeg
        return selfieResult
    }

    override suspend fun chooseDefault(characterId: String): Outcome<Avatar> {
        calls += "default:$characterId"
        return Outcome.Success(avatar.copy(defaultCharacterId = characterId))
    }

    override suspend fun equip(changes: Map<ItemSlot, Long?>): Outcome<Avatar> {
        calls += "equip:$changes"
        return equipResult(changes)
    }

    override suspend fun shop(slot: ItemSlot?): Outcome<Shop> {
        calls += "shop:$slot"
        return Outcome.Success(Shop(items.filter { slot == null || it.slot == slot }, 1_240))
    }

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
    fun `셀카를 보내면 끝난다(FR-155)`() = runTest(main.dispatcher) {
        val vm = AvatarSetupViewModel(repo)
        vm.events.test {
            vm.onSelfie(byteArrayOf(1, 2, 3))
            assertEquals(AvatarSetupEvent.Done, awaitItem())
        }
        assertEquals(3, repo.lastSelfie?.size)
    }

    @Test
    fun `하루 1회를 넘기면 안내하고 셀카를 막고 기본 캐릭터는 고를 수 있다(FR-157·FR-158)`() = runTest(main.dispatcher) {
        repo.selfieResult = Outcome.Failure(AppError.Rejected("SELFIE_LIMIT"))
        val vm = AvatarSetupViewModel(repo)
        vm.onSelfie(byteArrayOf(1))
        assertFalse(vm.state.value.canCreateSelfie)
        assertTrue(vm.state.value.error!!.startsWith("오늘은 이미"))
        vm.events.test {
            vm.onCharacterSelected(DefaultCharacter.CAT)
            vm.onConfirmCharacter()
            assertEquals(AvatarSetupEvent.Done, awaitItem())
        }
        assertTrue("default:CAT" in repo.calls)
    }

    @Test
    fun `네트워크 실패면 셀카를 다시 시도할 수 있다`() {
        repo.selfieResult = Outcome.Failure(AppError.Network)
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
        val vm = AvatarViewModel(repo)
        val s = vm.state.value
        assertEquals(1_240, s.avatar?.balance)
        assertEquals(listOf(1L, 2L), s.ownedInSlot.map { it.itemId })
        assertEquals("라임 러닝 티", s.itemName(1))
        vm.onSlot(ItemSlot.HAT)
        assertTrue(vm.state.value.ownedInSlot.isEmpty())
    }

    @Test
    fun `입은 아이템을 누르면 벗기고 다른 아이템은 바꿔 입힌다(FR-161)`() {
        val vm = AvatarViewModel(repo)
        vm.onToggle(items[1])
        assertEquals(2L, vm.state.value.avatar?.equipped?.get(ItemSlot.TOP))
        vm.onToggle(items[1])
        assertNull(vm.state.value.avatar?.equipped?.get(ItemSlot.TOP))
        assertEquals(listOf("equip:{TOP=2}", "equip:{TOP=null}"), repo.calls.filter { it.startsWith("equip") })
    }

    @Test
    fun `거부된 착용은 사유를 안내한다`() {
        repo.equipResult = { Outcome.Success(avatar.copy(rejected = mapOf(ItemSlot.TOP to "ITEM_NOT_OWNED"))) }
        val vm = AvatarViewModel(repo)
        vm.onToggle(items[1])
        assertEquals("상의: 가지고 있지 않은 아이템이에요", vm.state.value.message)
    }

    @Test
    fun `불러오지 못하면 오류`() {
        repo.avatarResult = Outcome.Failure(AppError.Network)
        val vm = AvatarViewModel(repo)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `셀카 얼굴이면 부위 그림을 받고 같은 얼굴은 다시 받지 않는다(FR-155)`() {
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1"))
        val vm = AvatarViewModel(repo)
        assertEquals(faceLayers, vm.state.value.faceLayers)
        vm.onResumed()
        vm.onResumed()
        assertEquals(1, repo.calls.count { it == "face:selfie:face/7/s1" })
    }

    @Test
    fun `기본 캐릭터면 받지 않고, 받지 못하면 비워 자리 그림을 그린다`() {
        val plain = AvatarViewModel(repo)
        assertEquals(emptyList(), plain.state.value.faceLayers)
        assertTrue(repo.calls.none { it.startsWith("face:") })
        repo.avatarResult = Outcome.Success(avatar.copy(faceKey = "selfie:face/7/s1"))
        repo.faceResult = Outcome.Failure(AppError.Rejected("FACE_IMAGE_NOT_FOUND"))
        val failed = AvatarViewModel(repo)
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
        assertEquals(items[2], vm.state.value.confirming)
        vm.onBuy()
        val s = vm.state.value
        assertEquals(990, s.shop?.balance)
        assertTrue(s.shop!!.items.first().owned)
        assertNull(s.confirming)
        assertTrue("buy:3" in repo.calls)
    }

    @Test
    fun `보유·판매 종료 아이템은 고를 수 없고 잔액 부족은 안내한다`() {
        val vm = ShopViewModel(repo)
        vm.onPick(items[0])
        vm.onPick(items[4])
        assertNull(vm.state.value.confirming)
        repo.purchaseResult = Outcome.Failure(AppError.Rejected("INSUFFICIENT_REWARD"))
        vm.onPick(items[3])
        vm.onBuy()
        assertEquals("리워드가 모자라요. 달리고 대결에서 이기면 모여요", vm.state.value.message)
        assertFalse(vm.state.value.buying)
    }

    @Test
    fun `셀카 변환 실패는 200이어도 실패 사유 안내와 기본 캐릭터 선택으로 이어진다`() {
        repo.selfieResult = Outcome.Failure(AppError.Rejected("NO_FACE"))
        val vm = AvatarSetupViewModel(repo)
        vm.onSelfie(byteArrayOf(1))
        assertEquals(
            "얼굴을 찾지 못했어요. 얼굴이 잘 보이게 다시 찍거나 기본 캐릭터를 골라 주세요",
            vm.state.value.error
        )
        assertFalse(vm.state.value.working)
        assertTrue(vm.state.value.canCreateSelfie)
    }
}

package com.a207.runkk.app.ui.avatar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarOutfit
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

private val NOW: Instant = Instant.parse("2026-11-09T10:00:00Z")
private val SINCE: Instant = Instant.parse("2026-11-01T00:00:00Z")

private fun bg(key: String, name: String, km: Long, unlockedAt: Instant?) = AvatarBackgrounds.Item(
    key,
    name,
    "DOMESTIC",
    Distance(km * 1_000),
    unlockedAt != null,
    unlockedAt,
    if (unlockedAt != null) Distance(0) else Distance(113_000)
)

private val incheon = bg("INCHEON", "인천", 60, Instant.parse("2026-10-20T00:00:00Z"))
private val kaesong = bg("KAESONG", "개성", 120, Instant.parse("2026-11-09T07:40:00Z"))
private val daejeon = bg("DAEJEON", "대전", 280, Instant.parse("2026-11-09T07:40:00Z"))
private val jeonju = bg("JEONJU", "전주", 400, null)

internal class FakeSeenStore(var seen: BackgroundUnlockSeenStore.Seen? = null) : BackgroundUnlockSeenStore {
    override fun load(): BackgroundUnlockSeenStore.Seen? = seen

    override fun save(seen: BackgroundUnlockSeenStore.Seen) {
        this.seen = seen
    }
}

/** W8 렌더러 값 옮기기, 배경 해금 판정(H-6), 내 러너(H-2)·상점 고르기(H-4) ViewModel. */
@RunWith(RobolectricTestRunner::class)
class AvatarW8LogicTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `체형 bodyVariant는 Rive 3단계로, 모르는 값은 기본이다`() {
        assertEquals(AvatarBodyShape.BASE, bodyShapeOf(0))
        assertEquals(AvatarBodyShape.CHANGE_1, bodyShapeOf(1))
        assertEquals(AvatarBodyShape.CHANGE_2, bodyShapeOf(2))
        assertEquals(AvatarBodyShape.BASE, bodyShapeOf(7))
        assertEquals(listOf(0, 1, 1, 2, 0), listOf("STAGE1", "STAGE2", "STAGE3", "STAGE4", "?").map(::bodyVariantOf))
    }

    @Test
    fun `착용 아이템은 그림 키로 Rive 의상 번호가 되고, 안 입었거나 모르는 키·모자는 0이다(contract-W8 6_3)`() {
        val shop = listOf(
            Shop.Item(1, ItemSlot.TOP, "라임 싱글렛", 300, true, true, imageKey = "top_lime_singlet"),
            Shop.Item(6, ItemSlot.TOP, "완주 기념 티", 300, true, true, imageKey = "top_finisher_tee"),
            Shop.Item(12, ItemSlot.BOTTOM, "조거", 500, true, true, imageKey = "bottom_jogger_pants"),
            Shop.Item(99, ItemSlot.SHOES, "새 신발", 500, true, true, imageKey = "shoes_unknown"),
            Shop.Item(31, ItemSlot.HAT, "캡", 200, true, true, imageKey = "hat_running_cap")
        )
        assertEquals(
            AvatarOutfit(top = 6, bottom = 2, shoes = 0),
            outfitOf(
                mapOf(ItemSlot.TOP to 6L, ItemSlot.BOTTOM to 12L, ItemSlot.SHOES to 99L, ItemSlot.HAT to 31L),
                shop
            )
        )
        assertEquals(AvatarOutfit(top = 1), outfitOf(mapOf(ItemSlot.TOP to 1L), shop))
        assertEquals(AvatarOutfit(), outfitOf(emptyMap(), shop))
    }

    @Test
    fun `H-6 기록이 없으면 지금 해금을 모두 본 것으로 채우고 팝업을 띄우지 않는다`() {
        val (unlock, seen) = findNewUnlocks(AvatarBackgrounds(null, listOf(incheon, kaesong, jeonju)), null, NOW)
        assertNull(unlock)
        assertEquals(BackgroundUnlockSeenStore.Seen(setOf("INCHEON", "KAESONG"), NOW), seen)
    }

    @Test
    fun `H-6 기록 뒤에 연 배경만 새 배경이고 해금 거리 내림차순, 첫 배경이 제목이다`() {
        val stored = BackgroundUnlockSeenStore.Seen(setOf("INCHEON"), SINCE)
        val (unlock, seen) =
            findNewUnlocks(AvatarBackgrounds(null, listOf(incheon, kaesong, daejeon, jeonju)), stored, NOW)
        assertEquals(listOf("DAEJEON", "KAESONG"), unlock?.items?.map { it.key })
        assertEquals("DAEJEON", unlock?.title?.key)
        assertEquals(stored, seen)
        val old = BackgroundUnlockSeenStore.Seen(emptySet(), NOW)
        assertNull(findNewUnlocks(AvatarBackgrounds(null, listOf(kaesong)), old, NOW).first, "기록 시작 전 해금")
    }

    private val repo = FakeAvatars().apply {
        backgroundsResult = Outcome.Success(AvatarBackgrounds(null, listOf(incheon, kaesong, daejeon, jeonju)))
    }
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)

    @Test
    fun `내 러너는 아바타와 내 러너를 읽어 렌더러 체형·러닝량을 정하고, 새 해금이 있으면 팝업을 띄운다`() {
        val store = FakeSeenStore(BackgroundUnlockSeenStore.Seen(setOf("INCHEON"), SINCE))
        val vm = AvatarRunnerViewModel(repo, store, clock)

        val s = vm.state.value
        assertEquals(3, s.runner?.recentRunCount)
        assertEquals(AvatarBodyShape.CHANGE_1, s.look.bodyShape)
        assertEquals(51, s.look.runAmount)
        assertNull(s.look.headLayers, "셀카 머리가 아니면 기본 머리")
        assertEquals(listOf("DAEJEON", "KAESONG"), s.unlock?.items?.map { it.key })

        vm.onUnlockChange()
        assertNull(vm.state.value.unlock)
        assertTrue("chooseBackground:DAEJEON" in repo.calls)
        assertEquals(setOf("INCHEON", "DAEJEON", "KAESONG"), store.seen?.keys)
        assertEquals("DAEJEON", vm.state.value.avatar?.backgroundKey)
    }

    @Test
    fun `내 러너 - 처음이면 기록만 남기고 팝업이 없고, 닫으면 다시 뜨지 않는다`() {
        val store = FakeSeenStore()
        val first = AvatarRunnerViewModel(repo, store, clock)
        assertNull(first.state.value.unlock)
        assertEquals(setOf("INCHEON", "KAESONG", "DAEJEON"), store.seen?.keys)

        store.seen = BackgroundUnlockSeenStore.Seen(setOf("INCHEON"), SINCE)
        val vm = AvatarRunnerViewModel(repo, store, clock)
        vm.onUnlockClose()
        assertNull(vm.state.value.unlock)
        assertNull(AvatarRunnerViewModel(repo, store, clock).state.value.unlock)
    }

    @Test
    fun `내 러너를 읽지 못하면 오류다`() {
        repo.runnerResult = Outcome.Failure(AppError.Network)
        val vm = AvatarRunnerViewModel(repo, FakeSeenStore(), clock)
        assertNull(vm.state.value.runner)
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun `상점 H-4 - 잠긴 칸은 고르지 않고 해금 조건을 안내하며 같은 칸을 다시 누르면 고르기를 푼다`() {
        val locked = Shop.Item(
            6,
            ItemSlot.TOP,
            "완주 기념 티",
            300,
            false,
            true,
            isLocked = true,
            unlockLabel = "하프 완주하면 열려요"
        )
        val vm = ShopViewModel(repo)
        vm.onPick(locked)
        assertNull(vm.state.value.selected)
        assertEquals(UiText.Plain("하프 완주하면 열려요"), vm.state.value.message)
        vm.onPick(items[2])
        assertEquals(items[2], vm.state.value.selected)
        vm.onPick(items[2])
        assertNull(vm.state.value.selected)
    }

    @Test
    fun `상점 - 사는 사이 잠겼으면(ITEM_LOCKED) 안내하고 목록을 다시 읽는다`() {
        repo.purchaseResult = Outcome.Failure(AppError.Rejected("ITEM_LOCKED"))
        val vm = ShopViewModel(repo)
        vm.onSlot(ItemSlot.HAT)
        vm.onPick(items[2])
        vm.onBuyRequest()
        vm.onBuy()
        assertEquals(UiText.Res(R.string.avatar_error_item_locked), vm.state.value.message)
        assertNull(vm.state.value.selected)
        assertEquals(2, repo.calls.count { it == "shop:HAT" })
    }
}

private val runner = AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4)

/** W8 화면: 내 러너(H-2), 꾸미기 탭·배경(H-3), 상점 구매 바(H-4), 해금 팝업(H-6). */
@RunWith(RobolectricTestRunner::class)
class AvatarW8ScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    @Test
    fun `H-2 내 러너 - 체형 칩, 피부톤 칩, 안내, 근거 문구와 꾸미기, 상점 가기`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarRunnerScreen(
                    AvatarRunnerUiState(isLoading = false, avatar = avatar, runner = runner),
                    AvatarRunnerActions(onDecorate = { calls += "decorate" }, onShop = { calls += "shop" })
                )
            }
        }
        compose.onNodeWithText(text(R.string.avatar_runner_body_chip, 2)).assertExists()
        compose.onNodeWithText(text(R.string.avatar_runner_skin_chip, "아침")).assertExists()
        compose.onNodeWithText("이번 주 3번 달렸어요. 4일 넘게 쉬면 배가 나오기 시작해요.").assertExists()
        compose.onNodeWithText("주로 오전 7시에 달려서 아침 톤이에요").assertExists()
        compose.onNodeWithText(text(R.string.avatar_runner_balance, "1,240")).assertExists()
        compose.onNodeWithContentDescription(text(R.string.avatar_runner_body_gauge, "보통", 2)).assertExists()
        compose.onNodeWithText(text(R.string.avatar_runner_decorate)).performClick()
        compose.onNodeWithText(text(R.string.avatar_decorate_go_shop)).performClick()
        assertEquals(listOf("decorate", "shop"), calls)
    }

    @Test
    fun `H-2 대표 시각이 없으면 일반 문구, 오후 시각은 오후로 쓴다`() {
        var state by mutableStateOf(
            AvatarRunnerUiState(isLoading = false, avatar = avatar, runner = runner.copy(typicalStartHour = null))
        )
        compose.setContent { RunkkTheme(reduceMotion = true) { AvatarRunnerScreen(state, AvatarRunnerActions()) } }
        compose.onNodeWithText(text(R.string.avatar_runner_skin_reason_none)).assertExists()
        state = state.copy(runner = runner.copy(typicalStartHour = 19, skinTone = "EVENING"))
        compose.onNodeWithText("주로 오후 7시에 달려서 저녁 톤이에요").assertExists()
    }

    @Test
    fun `H-6 해금 팝업 - 제목은 가장 먼 배경, 둘 이상이면 함께 연 배경을 모두 적고 배경 바꾸기·닫기`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarRunnerScreen(
                    AvatarRunnerUiState(
                        isLoading = false,
                        avatar = avatar,
                        runner = runner,
                        unlock = BackgroundUnlock(listOf(daejeon, kaesong))
                    ),
                    AvatarRunnerActions(onUnlockChange = { calls += "change" }, onUnlockClose = { calls += "close" })
                )
            }
        }
        compose.onNodeWithText(text(R.string.avatar_unlock_title, "대전")).assertExists()
        compose.onNodeWithText(text(R.string.avatar_unlock_together)).assertExists()
        compose.onNodeWithText("개성").assertExists()
        compose.onNodeWithText(text(R.string.avatar_unlock_change)).performClick()
        compose.onNodeWithText(text(R.string.avatar_unlock_close)).performClick()
        assertEquals(listOf("change", "close"), calls)
    }

    @Test
    fun `H-3 꾸미기 - 모자 탭은 악세서리, 잠긴 배경은 남은 거리, 기본 배경은 기본`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(isLoading = false, avatar = avatar, items = items, isBackgroundTab = true),
                    AvatarScreenActions(),
                    background = AvatarBackgroundUiState(
                        isLoading = false,
                        backgrounds = AvatarBackgrounds(null, listOf(incheon, jeonju))
                    )
                )
            }
        }
        compose.onNodeWithText(text(R.string.avatar_slot_accessory)).assertExists()
        compose.onNode(hasStateDescription(text(R.string.avatar_background_locked, "전주", 113))).assertExists()
        compose.onNode(hasStateDescription(text(R.string.avatar_background_selected, "기본"))).assertExists()
    }

    @Test
    fun `H-4 상점 - 칸을 고르면 아래 구매 바에 남는 잔액과 n에 사기, 잠긴 칸은 해금 조건, 보유는 소지 중`() {
        val locked = Shop.Item(
            6,
            ItemSlot.TOP,
            "완주 기념 티",
            300,
            false,
            true,
            isLocked = true,
            unlockLabel = "하프 완주하면 열려요"
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ShopScreen(
                    ShopUiState(isLoading = false, shop = Shop(items + locked, 1_240), selected = items[2]),
                    ShopActions(onBuyRequest = { calls += "buy" }, onPick = { calls += "pick:${it.itemId}" })
                )
            }
        }
        compose.onNodeWithText(text(R.string.avatar_shop_left_after, "990")).assertExists()
        compose.onNodeWithText(text(R.string.avatar_shop_buy_for, "250")).performClick()
        compose.onNodeWithText("하프 완주하면 열려요").assertExists()
        compose.onNodeWithContentDescription("라임 러닝 티, 상의, 소지 중").performClick()
        assertEquals(listOf("buy", "pick:1"), calls)
    }
}

/** W8 스크린샷(H-2 내 러너, H-4 상점 고름, H-6 해금 팝업). Rive는 네이티브라 시험에서는 앞 단계 그림으로 바뀐다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class AvatarW8ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun runner() = compose.captureRunkkScreen("H2_avatar_runner") {
        AvatarRunnerScreen(
            AvatarRunnerUiState(isLoading = false, avatar = avatar, runner = runner),
            AvatarRunnerActions()
        )
    }

    @Test
    fun shopSelected() = compose.captureRunkkScreen("H4_shop_selected") {
        val locked = Shop.Item(
            6,
            ItemSlot.TOP,
            "완주 기념 티",
            300,
            false,
            true,
            isLocked = true,
            unlockLabel = "하프 완주하면 열려요"
        )
        ShopScreen(
            ShopUiState(isLoading = false, shop = Shop(items.take(3) + locked, 1_240), selected = items[2]),
            ShopActions()
        )
    }

    @Test
    fun unlockPopup() = compose.captureRunkkScreen("H6_background_unlock") {
        AvatarRunnerScreen(
            AvatarRunnerUiState(
                isLoading = false,
                avatar = avatar,
                runner = runner,
                unlock = BackgroundUnlock(listOf(daejeon, kaesong))
            ),
            AvatarRunnerActions()
        )
    }
}

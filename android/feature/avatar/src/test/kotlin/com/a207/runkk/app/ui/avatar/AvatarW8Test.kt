package com.a207.runkk.app.ui.avatar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarOutfit
import com.a207.runkk.app.core.avatar.R as AvatarAssetR
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
import com.a207.runkk.app.domain.avatar.SelfieProgress
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
private val chuncheon = bg("CHUNCHEON", "춘천", 150, Instant.parse("2026-11-09T07:40:00Z"))
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
            AvatarOutfit(top = 3, bottom = 3, shoes = 0),
            outfitOf(
                mapOf(ItemSlot.TOP to 6L, ItemSlot.BOTTOM to 12L, ItemSlot.SHOES to 99L, ItemSlot.HAT to 31L),
                shop
            )
        )
        assertEquals(AvatarOutfit(top = 1), outfitOf(mapOf(ItemSlot.TOP to 1L), shop))
        assertEquals(AvatarOutfit(), outfitOf(emptyMap(), shop))
    }

    /**
     * 실기기 2차 시험 관찰: 「라임 러닝화」가 파란 하이탑으로, 「조거 팬츠」가 레깅스로 그려졌다. 대응표는 그림 키를 실제로 그려지는 의상
     * (core/avatar 의상 번호 이름표 avatar_outfit_*_names)과 같은 모양에 둔다. 상점 이름은 서버 V62가 이 모양에 맞춘다.
     */
    @Test
    fun `상점 그림 키는 같은 모양의 Rive 의상으로 그린다(contract-W8 6_3)`() {
        val res = RuntimeEnvironment.getApplication().resources
        fun drawn(slot: ItemSlot, key: String): String {
            val outfit = outfitOf(mapOf(slot to 1L), listOf(Shop.Item(1, slot, key, 1, true, true, imageKey = key)))
            val (names, index) = when (slot) {
                ItemSlot.TOP -> AvatarAssetR.array.avatar_outfit_top_names to outfit.top
                ItemSlot.BOTTOM -> AvatarAssetR.array.avatar_outfit_bottom_names to outfit.bottom
                else -> AvatarAssetR.array.avatar_outfit_shoes_names to outfit.shoes
            }
            return res.getStringArray(names)[index]
        }
        val expected = mapOf(
            (ItemSlot.TOP to "top_lime_singlet") to "민소매",
            (ItemSlot.TOP to "top_crew_tee") to "카라티",
            (ItemSlot.TOP to "top_finisher_tee") to "긴팔 맨투맨",
            (ItemSlot.TOP to "top_winter_hoodie") to "후드티",
            (ItemSlot.TOP to "top_dawn_windbreaker") to "집업 재킷",
            (ItemSlot.TOP to "top_cherry_vest") to "코랄 집업 후드",
            (ItemSlot.BOTTOM to "bottom_running_shorts") to "러닝 쇼츠",
            (ItemSlot.BOTTOM to "bottom_leggings") to "레깅스",
            (ItemSlot.BOTTOM to "bottom_jogger_pants") to "조거 팬츠",
            (ItemSlot.SHOES to "shoes_basic_runner") to "로우탑",
            (ItemSlot.SHOES to "shoes_carbon_racer") to "트레일 러너",
            (ItemSlot.SHOES to "shoes_lime_runner") to "코랄 스니커즈"
        )
        expected.forEach { (item, name) -> assertEquals(name, drawn(item.first, item.second), item.second) }
    }

    @Test
    fun `H-6 기록이 없으면 지금 해금을 모두 본 것으로 채우고 팝업을 띄우지 않는다`() {
        val (unlock, seen) = findNewUnlocks(AvatarBackgrounds(null, listOf(incheon, chuncheon, jeonju)), null, NOW)
        assertNull(unlock)
        assertEquals(BackgroundUnlockSeenStore.Seen(setOf("INCHEON", "CHUNCHEON"), NOW), seen)
    }

    @Test
    fun `H-6 기록 뒤에 연 배경만 새 배경이고 해금 거리 내림차순, 첫 배경이 제목이다`() {
        val stored = BackgroundUnlockSeenStore.Seen(setOf("INCHEON"), SINCE)
        val (unlock, seen) =
            findNewUnlocks(AvatarBackgrounds(null, listOf(incheon, chuncheon, daejeon, jeonju)), stored, NOW)
        assertEquals(listOf("DAEJEON", "CHUNCHEON"), unlock?.items?.map { it.key })
        assertEquals("DAEJEON", unlock?.title?.key)
        assertEquals(stored, seen)
        val old = BackgroundUnlockSeenStore.Seen(emptySet(), NOW)
        assertNull(findNewUnlocks(AvatarBackgrounds(null, listOf(chuncheon)), old, NOW).first, "기록 시작 전 해금")
    }

    @Test
    fun `H-6 기기 기록에 남은 지운 키(KAESONG)는 서버 목록에 없으면 무시한다(D-성-124)`() {
        val stored = BackgroundUnlockSeenStore.Seen(setOf("INCHEON", "KAESONG"), SINCE)
        val (unlock, _) =
            findNewUnlocks(AvatarBackgrounds(null, listOf(incheon, chuncheon, daejeon, jeonju)), stored, NOW)
        assertEquals(listOf("DAEJEON", "CHUNCHEON"), unlock?.items?.map { it.key })
        assertEquals("DAEJEON", unlock?.title?.key)
    }

    private val repo = FakeAvatars().apply {
        backgroundsResult = Outcome.Success(AvatarBackgrounds(null, listOf(incheon, chuncheon, daejeon, jeonju)))
    }
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)

    private fun runnerVm(
        store: BackgroundUnlockSeenStore,
        level: Int? = 12,
        stats: RunnerStats? = RunnerStats(3, Distance(12_400), 4)
    ) = AvatarRunnerViewModel(repo, store, clock, headsOf(repo), storeOf(repo), level = { level }, stats = { stats })

    @Test
    fun `내 러너는 아바타와 내 러너를 읽어 렌더러 체형·러닝량을 정하고, 새 해금이 있으면 팝업을 띄운다`() {
        val store = FakeSeenStore(BackgroundUnlockSeenStore.Seen(setOf("INCHEON"), SINCE))
        val vm = runnerVm(store)

        val s = vm.state.value
        assertEquals(3, s.runner?.recentRunCount)
        assertEquals(AvatarBodyShape.CHANGE_1, s.look.bodyShape)
        assertEquals(51, s.look.runAmount)
        assertNull(s.look.headLayers, "셀카 머리가 아니면 기본 머리")
        assertEquals(listOf("DAEJEON", "CHUNCHEON"), s.unlock?.items?.map { it.key })
        assertEquals(12, s.level)
        assertEquals(RunnerStats(3, Distance(12_400), 4), s.stats, "숫자 타일은 이번 주 횟수·거리와 연속 러닝 일수")
        assertEquals(3, s.unlockedBackgrounds)
        assertEquals(4, s.totalBackgrounds)

        vm.onUnlockChange()
        assertNull(vm.state.value.unlock)
        assertTrue("chooseBackground:DAEJEON" in repo.calls)
        assertEquals(setOf("INCHEON", "DAEJEON", "CHUNCHEON"), store.seen?.keys)
        assertEquals("DAEJEON", vm.state.value.avatar?.backgroundKey)
    }

    @Test
    fun `내 러너 - 처음이면 기록만 남기고 팝업이 없고, 닫으면 다시 뜨지 않는다`() {
        val store = FakeSeenStore()
        val first = runnerVm(store)
        assertNull(first.state.value.unlock)
        assertEquals(setOf("INCHEON", "CHUNCHEON", "DAEJEON"), store.seen?.keys)

        store.seen = BackgroundUnlockSeenStore.Seen(setOf("INCHEON"), SINCE)
        val vm = runnerVm(store)
        vm.onUnlockClose()
        assertNull(vm.state.value.unlock)
        assertNull(runnerVm(store).state.value.unlock)
    }

    @Test
    fun `대시보드를 못 읽으면 숫자 타일 값이 없다`() {
        assertNull(runnerVm(FakeSeenStore(), stats = null).state.value.stats)
    }

    @Test
    fun `내 러너를 읽지 못하면 오류다`() {
        repo.runnerResult = Outcome.Failure(AppError.Network)
        val vm = runnerVm(FakeSeenStore())
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
        val vm = ShopViewModel(repo, headsOf(repo), storeOf(repo))
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
        val vm = ShopViewModel(repo, headsOf(repo), storeOf(repo))
        vm.onSlot(ItemSlot.HAT)
        vm.onPick(items[2])
        vm.onBuyRequest()
        vm.onBuy()
        assertEquals(UiText.Res(R.string.avatar_error_item_locked), vm.state.value.message)
        assertNull(vm.state.value.selected)
        assertEquals(2, repo.calls.count { it == "shop:null" })
    }
}

private val runner = AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(12_400), 51, 4)

/** 내 러너·꾸미기·상점에 보이면 안 되는 체형·피부톤 설명(사용자 지시 「체형 배가 블록 피부색 낮형 … 빼」, design-oo 05-30 [임시:D-성-128]). */
private val FORBIDDEN = listOf("체형", "배가", "볼록", "피부", "낮형", "아침형", "저녁형", "새벽형", "밤형", "톤이에요", "번 달렸어요", "근육")

/** W8 화면(피그마 제안): 내 러너 B안, 꾸미기 A안, 상점 A안, 해금 팝업(H-6). */
@RunWith(RobolectricTestRunner::class)
class AvatarW8ScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun assertNoBodyOrSkinText() {
        FORBIDDEN.forEach { word -> compose.onNodeWithText(word, substring = true).assertDoesNotExist() }
        FORBIDDEN.forEach { word ->
            compose.onAllNodes(hasContentDescription(word, substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun `내 러너 B안 - 레벨 칩, 숫자 타일 셋, 얼굴·배경 진입, 꾸미기·상점(설명 문장 없음)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarRunnerScreen(
                    AvatarRunnerUiState(
                        isLoading = false,
                        avatar = avatar,
                        runner = runner,
                        level = 12,
                        stats = RunnerStats(3, Distance(12_400), 4),
                        unlockedBackgrounds = 3,
                        totalBackgrounds = 12
                    ),
                    AvatarRunnerActions(
                        onDecorate = { calls += "decorate" },
                        onShop = { calls += "shop" },
                        onFace = { calls += "face" },
                        onBackground = { calls += "background" }
                    )
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.avatar_runner_level_description, 12)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.avatar_runner_runs_description, 3)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.avatar_runner_distance_description, "12.4")).assertExists()
        compose.onNodeWithContentDescription(text(R.string.avatar_runner_streak_description, 4)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.avatar_shop_balance_description, 1_240)).assertExists()
        compose.onNodeWithText("3/12").assertExists()
        compose.onNodeWithText(text(R.string.avatar_runner_face)).performClick()
        compose.onNodeWithText(text(R.string.avatar_tab_background)).performClick()
        compose.onNodeWithText(text(R.string.avatar_runner_decorate)).performClick()
        compose.onNodeWithText(text(R.string.avatar_shop_title)).performClick()
        assertEquals(listOf("face", "background", "decorate", "shop"), calls)
        assertNoBodyOrSkinText()
    }

    @Test
    fun `내 러너 - 대시보드를 못 읽으면 숫자 타일은 「-」다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarRunnerScreen(
                    AvatarRunnerUiState(isLoading = false, avatar = avatar, runner = runner),
                    AvatarRunnerActions()
                )
            }
        }
        // 타일은 그림 하나로 읽고(숫자는 「-」로 그린다) 모르는 값은 읽어 주지 않는다.
        compose.onAllNodes(hasContentDescription("이번 주", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("연속", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `내 러너 - 셀카를 만드는 중이면 얼굴 줄에 진행 표시를 단다(피그마 AV-7 4)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarRunnerScreen(
                    AvatarRunnerUiState(
                        isLoading = false,
                        avatar = avatar,
                        runner = runner,
                        selfie = SelfieProgress.Working("job-1")
                    ),
                    AvatarRunnerActions()
                )
            }
        }
        compose.onNode(hasStateDescription(text(R.string.avatar_selfie_badge_working))).assertExists()
    }

    @Test
    fun `내 러너 - 읽는 동안에도 무대 자리를 먼저 그리고 못 읽으면 오류와 다시 시도`() {
        var state by mutableStateOf(AvatarRunnerUiState(isLoading = true))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarRunnerScreen(state, AvatarRunnerActions(onRetry = { calls += "retry" }))
            }
        }
        compose.onNodeWithContentDescription(text(R.string.avatar_runner_figure)).assertExists()
        state = AvatarRunnerUiState(isLoading = false)
        compose.onNodeWithText(text(R.string.avatar_runner_load_failed)).assertExists()
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
                        unlock = BackgroundUnlock(listOf(daejeon, chuncheon))
                    ),
                    AvatarRunnerActions(onUnlockChange = { calls += "change" }, onUnlockClose = { calls += "close" })
                )
            }
        }
        compose.onNodeWithText(text(R.string.avatar_unlock_title, "대전")).assertExists()
        compose.onNodeWithText(text(R.string.avatar_unlock_together)).assertExists()
        compose.onNodeWithText("춘천").assertExists()
        compose.onNodeWithText(text(R.string.avatar_unlock_change)).performClick()
        compose.onNodeWithText(text(R.string.avatar_unlock_close)).performClick()
        assertEquals(listOf("change", "close"), calls)
    }

    @Test
    fun `꾸미기 A안 - 모자 탭은 악세서리, 잠긴 배경은 남은 거리, 체형·피부색 칩과 빈 목록 문장 없음`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(
                        isLoading = false,
                        avatar = avatar,
                        items = items,
                        draft = avatar.equipped,
                        isBackgroundTab = true
                    ),
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
        assertNoBodyOrSkinText()
    }

    @Test
    fun `꾸미기 A안 - 잠긴 칸은 자물쇠와 짧은 조건을 보이고 해금 조건을 읽는다`() {
        val locked = Shop.Item(
            6,
            ItemSlot.TOP,
            "완주 기념 맨투맨",
            300,
            false,
            true,
            imageKey = "top_finisher_tee",
            isLocked = true,
            unlockLabel = "하프 완주하면 열려요"
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(isLoading = false, avatar = avatar, items = items + locked, draft = avatar.equipped),
                    AvatarScreenActions(onToggle = { calls += "toggle:${it.itemId}" })
                )
            }
        }
        compose.onNodeWithText(text(R.string.avatar_unlock_short_half), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("하프 완주하면 열려요", substring = true).performClick()
        assertEquals(listOf("toggle:6"), calls)
    }

    @Test
    fun `상점 A안 - 고른 칸은 아래 단추에 이름과 가격, 가진 칸은 보유, 잠긴 칸은 조건`() {
        val locked = Shop.Item(
            6,
            ItemSlot.TOP,
            "완주 기념 맨투맨",
            300,
            false,
            true,
            imageKey = "top_finisher_tee",
            isLocked = true,
            unlockLabel = "하프 완주하면 열려요"
        )
        val unowned = Shop.Item(7, ItemSlot.TOP, "크루 카라티", 300, false, true, imageKey = "top_crew_tee")
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ShopScreen(
                    ShopUiState(isLoading = false, shop = Shop(items + locked + unowned, 1_240), selected = unowned),
                    ShopActions(onBuyRequest = { calls += "buy" }, onPick = { calls += "pick:${it.itemId}" })
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.avatar_shop_buy_description, "크루 카라티", 300)).performClick()
        compose.onNodeWithContentDescription("하프 완주하면 열려요", substring = true).assertExists()
        compose.onNodeWithContentDescription("라벤더 싱글렛, ${text(R.string.avatar_item_owned_chip)}").performClick()
        assertEquals(listOf("buy", "pick:2"), calls)
        assertNoBodyOrSkinText()
    }

    @Test
    fun `상점 A안 - 가진 칸을 고르면 입기 단추, 입는 중이면 끈다`() {
        var state by mutableStateOf(
            ShopUiState(isLoading = false, shop = Shop(items, 1_240), avatar = avatar, selected = items[1])
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) { ShopScreen(state, ShopActions(onEquip = { calls += "equip" })) }
        }
        compose.onNodeWithText(text(R.string.avatar_item_put_on)).performClick()
        assertEquals(listOf("equip"), calls)
        state = state.copy(selected = items[0])
        compose.onAllNodesWithText(text(R.string.avatar_item_wearing)).onLast().assertIsNotEnabled()
    }
}

/** 상점 그림 규칙(design-oo 05-30 [임시:D-성-113]): 그림 키 k의 그림은 drawable shop_k이고 V62 시드 16종이 모두 있다. */
@RunWith(RobolectricTestRunner::class)
class ShopArtTest {
    private val keys = listOf(
        "top_lime_singlet", "top_dawn_windbreaker", "top_crew_tee", "top_winter_hoodie", "top_cherry_vest",
        "top_finisher_tee", "bottom_running_shorts", "bottom_jogger_pants", "bottom_leggings",
        "shoes_basic_runner", "shoes_lime_runner", "shoes_carbon_racer", "hat_running_cap", "hat_headband",
        "hat_beanie", "hat_ten_runs_cap"
    )

    @Test
    fun `그림 키 k는 drawable shop_k로 찾고 모르는 키는 실루엣이다`() {
        val resources = RuntimeEnvironment.getApplication().resources
        keys.forEach { key ->
            val res = shopArtRes(key)
            assertTrue(res != null, key)
            assertEquals("shop_$key", resources.getResourceEntryName(res!!))
        }
        assertNull(shopArtRes("unknown"))
        assertNull(shopArtRes(null))
    }

    @Test
    fun `잠긴 세 키만 짧은 조건을 둔다(21_1km, 100km, 10회)`() {
        assertEquals("21.1km", text(unlockShortLabelRes("top_finisher_tee")!!))
        assertEquals("100km", text(unlockShortLabelRes("shoes_carbon_racer")!!))
        assertEquals("10회", text(unlockShortLabelRes("hat_ten_runs_cap")!!))
        assertNull(unlockShortLabelRes("top_crew_tee"))
    }
}

/** W8 스크린샷(내 러너 B안, 상점 A안, 해금 팝업). Rive는 네이티브라 시험에서는 앞 단계 그림으로 바뀐다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class AvatarW8ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val runnerState = AvatarRunnerUiState(
        isLoading = false,
        avatar = avatar,
        runner = runner,
        level = 12,
        stats = RunnerStats(3, Distance(12_400), 4),
        unlockedBackgrounds = 3,
        totalBackgrounds = 12
    )

    @Test
    fun runner() = compose.captureRunkkScreen("H2_avatar_runner") {
        AvatarRunnerScreen(runnerState, AvatarRunnerActions())
    }

    @Test
    fun runnerLight() = compose.captureRunkkScreen("H2_avatar_runner_light", isDarkTheme = false) {
        AvatarRunnerScreen(runnerState.copy(selfie = SelfieProgress.Working("job-1")), AvatarRunnerActions())
    }

    @Test
    fun shopSelected() = compose.captureRunkkScreen("H4_shop_selected") {
        val locked = Shop.Item(
            6,
            ItemSlot.TOP,
            "완주 기념 맨투맨",
            300,
            false,
            true,
            imageKey = "top_finisher_tee",
            isLocked = true,
            unlockLabel = "하프 완주하면 열려요"
        )
        val shopItems = listOf(
            Shop.Item(5, ItemSlot.TOP, "코랄 집업 후드", 400, false, true, imageKey = "top_cherry_vest"),
            Shop.Item(1, ItemSlot.TOP, "민트 싱글렛", 300, true, true, imageKey = "top_lime_singlet"),
            Shop.Item(3, ItemSlot.TOP, "크루 카라티", 300, false, true, imageKey = "top_crew_tee"),
            Shop.Item(4, ItemSlot.TOP, "겨울 후디", 700, false, true, imageKey = "top_winter_hoodie"),
            Shop.Item(2, ItemSlot.TOP, "새벽 바람막이", 500, false, true, imageKey = "top_dawn_windbreaker"),
            locked
        )
        ShopScreen(
            ShopUiState(isLoading = false, shop = Shop(shopItems, 1_240), avatar = avatar, selected = shopItems[0]),
            ShopActions()
        )
    }

    @Test
    fun unlockPopup() = compose.captureRunkkScreen("H6_background_unlock") {
        AvatarRunnerScreen(
            runnerState.copy(unlock = BackgroundUnlock(listOf(daejeon, chuncheon))),
            AvatarRunnerActions()
        )
    }
}

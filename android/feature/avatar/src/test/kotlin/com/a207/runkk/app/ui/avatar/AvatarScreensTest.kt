package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.runkkAvatarCharacterTag
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun str(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

/** 꾸미기 시험 상태: 착용 그대로(저장하지 않은 변경 없음). */
private fun decorating(isBackgroundTab: Boolean = false) = AvatarUiState(
    isLoading = false,
    avatar = avatar,
    items = items,
    draft = avatar.equipped,
    isBackgroundTab = isBackgroundTab
)

/** 얼굴 만들기(피그마 AV-7 1안)·꾸미기(A안)·상점 확인 화면: 단추·접근성 라벨, 셀카 줄이기. */
@RunWith(RobolectricTestRunner::class)
class AvatarScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun setup(state: AvatarSetupUiState, actions: AvatarSetupActions = AvatarSetupActions()) {
        compose.setContent { RunkkTheme(reduceMotion = true) { AvatarSetupScreen(state, actions) } }
    }

    @Test
    fun `1 고르기 - 셀카 찍기, 앨범, 기본 얼굴 4종, 고른 기본 얼굴만 저장 단추를 보인다(J7)`() {
        setup(
            AvatarSetupUiState(isLoading = false, faceKey = "default:FOX", selected = DefaultCharacter.FOX),
            AvatarSetupActions(
                onSelfie = { calls += "selfie" },
                onAlbum = { calls += "album" },
                onCharacter = { calls += it.name }
            )
        )
        compose.onNodeWithText(str(R.string.avatar_setup_selfie_daily)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_save)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.avatar_setup_take_selfie)).performClick()
        compose.onNodeWithText(str(R.string.avatar_setup_pick_album)).performClick()
        compose.onNodeWithContentDescription(str(R.string.avatar_character_bear)).performClick()
        assertEquals(listOf("selfie", "album", "BEAR"), calls)
        DefaultCharacter.entries.forEach {
            compose.onAllNodesWithTag(runkkAvatarCharacterTag(it.name), useUnmergedTree = true).onFirst().assertExists()
        }
    }

    @Test
    fun `1 고르기 - 다른 기본 얼굴을 고르면 저장 단추가 뜨고 누르면 저장한다(FR-158)`() {
        setup(
            AvatarSetupUiState(isLoading = false, faceKey = "default:FOX", selected = DefaultCharacter.BEAR),
            AvatarSetupActions(onSave = { calls += "save" })
        )
        compose.onNodeWithText(str(R.string.avatar_setup_save)).performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun `1 고르기 - 오늘 다 만들었으면 셀카·앨범을 끄고 「내일 다시」`() {
        setup(AvatarSetupUiState(isLoading = false, canCreateSelfie = false))
        compose.onNodeWithText(str(R.string.avatar_setup_selfie_used)).assertExists()
        compose.onNode(
            hasText(str(R.string.avatar_setup_take_selfie)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)
        )
            .assertExists()
    }

    @Test
    fun `2 사진 확인 - 올린 사진을 크게 보이고 동의 뒤 만들기 시작, 다시 고르기`() {
        val jpeg = SelfieEncoder.encode(Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888))
        setup(
            AvatarSetupUiState(isLoading = false, phase = SetupPhase.CONFIRM, photo = jpeg, hasFaceConsent = true),
            AvatarSetupActions(onStart = { calls += "start" }, onRepick = { calls += "repick" })
        )
        compose.onNodeWithContentDescription(str(R.string.avatar_setup_sent_photo)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_start)).performClick()
        compose.onNodeWithText(str(R.string.avatar_setup_repick)).performClick()
        assertEquals(listOf("start", "repick"), calls)
    }

    @Test
    fun `2 사진 확인 - 동의 체크를 누르면 동의하고 동의 전에는 만들기 시작을 끈다`() {
        setup(
            AvatarSetupUiState(isLoading = false, phase = SetupPhase.CONFIRM, photo = byteArrayOf(1)),
            AvatarSetupActions(onFaceConsent = { calls += "consent" })
        )
        val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        compose.onNode(isCheckbox).assertIsOff().performClick()
        compose.onNodeWithText(str(R.string.avatar_setup_start)).assertIsNotEnabled()
        assertEquals(listOf("consent"), calls)
    }

    @Test
    fun `3 만드는 중 - 「나중에 다시 확인해도 돼요」와 다른 화면 둘러보기, 가짜 % 없음`() {
        setup(
            AvatarSetupUiState(isLoading = false, phase = SetupPhase.WORKING),
            AvatarSetupActions(onLater = { calls += "later" })
        )
        compose.onNodeWithText(str(R.string.avatar_setup_making)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_later_title)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_later_body)).assertExists()
        compose.onNodeWithText("%", substring = true).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.avatar_setup_browse)).performClick()
        assertEquals(listOf("later"), calls)
    }

    @Test
    fun `새 얼굴 - 바로 적용했다고 알리고 확인으로 돌아간다(D-성-118)`() {
        setup(
            AvatarSetupUiState(isLoading = false, phase = SetupPhase.DONE),
            AvatarSetupActions(onDoneConfirm = { calls += "ok" })
        )
        compose.onNodeWithText(str(R.string.avatar_setup_done_headline)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_done_confirm)).performClick()
        assertEquals(listOf("ok"), calls)
    }

    @Test
    fun `3′ 실패 - 사유와 오늘 횟수는 그대로, 다른 사진 고르기·기본 얼굴 쓰기(FR-155)`() {
        setup(
            AvatarSetupUiState(
                isLoading = false,
                phase = SetupPhase.FAILED,
                failure = UiText.Res(R.string.avatar_error_no_face)
            ),
            AvatarSetupActions(onRetry = { calls += "retry" }, onUseDefault = { calls += "default" })
        )
        compose.onNodeWithText(str(R.string.avatar_setup_failed_title)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_error_no_face)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_count_kept)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_other_photo)).performClick()
        compose.onNodeWithText(str(R.string.avatar_setup_use_default)).performClick()
        assertEquals(listOf("retry", "default"), calls)
    }

    @Test
    fun `꾸미기 - 무대를 그림 하나로 읽고 칸을 누르면 입혀 보며 되돌리기·상점·저장·배경 탭으로 간다(A안)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    decorating().copy(draft = mapOf(ItemSlot.TOP to 2L)),
                    AvatarScreenActions(
                        onShop = { calls += "shop" },
                        onToggle = { calls += "toggle:${it.itemId}" },
                        onUndo = { calls += "undo" },
                        onBackgroundTab = { calls += "background" },
                        onSave = { calls += "save" }
                    )
                )
            }
        }
        compose.onNodeWithContentDescription(
            str(
                R.string.avatar_figure_description,
                str(R.string.avatar_face_default, str(R.string.avatar_character_fox))
            ) +
                str(R.string.avatar_figure_equipped_format, str(R.string.avatar_slot_top), "라벤더 싱글렛")
        ).assertExists()
        compose.onNode(hasStateDescription(str(R.string.avatar_item_wearing))).assertIsSelected()
        compose.onNodeWithContentDescription("라임 러닝 티", substring = true).performClick()
        compose.onNodeWithContentDescription(str(R.string.avatar_decorate_undo)).performClick()
        compose.onNodeWithContentDescription(str(R.string.avatar_decorate_more_in_shop)).performClick()
        compose.onAllNodesWithText(str(R.string.avatar_shop_title)).onLast().performClick()
        compose.onNodeWithText(str(R.string.avatar_tab_background)).performClick()
        compose.onNodeWithText(str(R.string.avatar_decorate_save)).performClick()
        assertEquals(listOf("toggle:1", "undo", "shop", "shop", "background", "save"), calls)
    }

    @Test
    fun `꾸미기 - 기본 캐릭터 키(default)면 그 캐릭터로 읽고 체형 이름은 읽지 않는다(D-성-128)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    decorating().copy(
                        avatar = avatar.copy(faceKey = "default:BEAR", defaultCharacterId = "BEAR"),
                        draft = emptyMap()
                    ),
                    AvatarScreenActions()
                )
            }
        }
        compose.onNodeWithContentDescription(
            str(
                R.string.avatar_figure_description,
                str(R.string.avatar_face_default, str(R.string.avatar_character_bear))
            )
        ).assertExists()
    }

    @Test
    fun `상점 - 사기 전에 가격과 구매 후 잔액으로 확인을 받는다(J5)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ShopScreen(
                    ShopUiState(isLoading = false, shop = Shop(items, 1_240), confirming = items[2]),
                    ShopActions(onBuy = { calls += "buy" })
                )
            }
        }
        compose.onNodeWithText(str(R.string.avatar_shop_confirm_title, "런 캡", "을")).assertExists()
        compose.onNodeWithText(
            str(R.string.avatar_shop_confirm_message, "${items[2].price}", "${1_240 - items[2].price}")
        ).assertExists()
        compose.onAllNodesWithText(str(R.string.avatar_shop_buy)).onLast().performClick()
        assertEquals(listOf("buy"), calls)
    }

    @Test
    fun `셀카는 긴 변 1024px JPEG 1MB 이하로 줄인다(D-23)`() {
        val big = Bitmap.createBitmap(3_000, 2_000, Bitmap.Config.ARGB_8888)
        val jpeg = SelfieEncoder.encode(big)
        assertTrue(jpeg.size in 1..SelfieEncoder.MAX_BYTES)
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
    }

    @Test
    fun `부위 문구와 체형 단계`() {
        assertEquals(3, bodyStage("STAGE3"))
        assertEquals(R.string.avatar_slot_hat, ItemSlot.HAT.labelRes)
        assertEquals(R.string.avatar_slot_accessory, ItemSlot.HAT.decorateLabelRes)
    }

    @Test
    fun `배경 탭 - 칸은 라디오로 알리고 잠김 상태를 읽으며 누르면 고른다(J3, FR-201)`() {
        val chosen = mutableListOf<String>()
        var defaults = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    decorating(isBackgroundTab = true),
                    AvatarScreenActions(
                        onChooseBackground = { chosen += it.key },
                        onChooseDefaultBackground = { defaults++ }
                    ),
                    background = AvatarBackgroundUiState(
                        isLoading = false,
                        backgrounds = sampleBackgrounds.copy(selectedKey = "INCHEON")
                    )
                )
            }
        }
        compose.onNodeWithText(str(R.string.avatar_background_unlocked_count, 2, 3)).assertExists()
        compose.onNode(hasContentDescription("인천") and hasStateDescription("인천, 사용 중")).assertIsSelected()
        compose.onNode(hasContentDescription("베이징") and hasStateDescription("베이징, 830km 더 달리면 해금"))
            .assertIsNotSelected()
            .performClick()
        compose.onNodeWithContentDescription(str(R.string.avatar_background_default)).performClick()
        assertEquals(listOf("BEIJING"), chosen)
        assertEquals(1, defaults)
    }

    @Test
    fun `2 사진 확인 - 동의했으면 체크된 채 둔다`() {
        setup(
            AvatarSetupUiState(
                isLoading = false,
                phase = SetupPhase.CONFIRM,
                photo = byteArrayOf(1),
                hasFaceConsent = true
            )
        )
        val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        compose.onNode(isCheckbox).assertIsOn()
    }
}

/** 아바타 스크린샷(J3~J5 꾸미기·상점, J7 얼굴 만들기 단계). Rive는 네이티브라 시험에서는 앞 단계 그림으로 바뀐다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class AvatarScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val photo by lazy { SelfieEncoder.encode(Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)) }

    @Test
    fun setup() = compose.captureRunkkScreen("J7_avatar_face") {
        AvatarSetupScreen(AvatarSetupUiState(isLoading = false, selected = DefaultCharacter.FOX), AvatarSetupActions())
    }

    @Test
    fun setupConfirm() = compose.captureRunkkScreen("J7_avatar_face_confirm") {
        AvatarSetupScreen(
            AvatarSetupUiState(isLoading = false, phase = SetupPhase.CONFIRM, photo = photo, hasFaceConsent = true),
            AvatarSetupActions()
        )
    }

    /** 셀카 접수 뒤 서버가 얼굴을 만드는 중(피그마 AV-7 3): 보낸 사진, 돌아가는 고리, 나중에 확인 안내. */
    @Test
    fun setupMakingFace() = compose.captureRunkkScreen("J7_avatar_face_making") {
        AvatarSetupScreen(
            AvatarSetupUiState(isLoading = false, phase = SetupPhase.WORKING, photo = photo),
            AvatarSetupActions()
        )
    }

    @Test
    fun setupSelfieFailed() = compose.captureRunkkScreen("J7_avatar_face_failed") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                isLoading = false,
                phase = SetupPhase.FAILED,
                photo = photo,
                failure = UiText.Res(R.string.avatar_error_no_face)
            ),
            AvatarSetupActions()
        )
    }

    @Test
    fun setupDone() = compose.captureRunkkScreen("J7_avatar_face_done") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                isLoading = false,
                phase = SetupPhase.DONE,
                look = AvatarLook(),
                faceKey = "default:FOX"
            ),
            AvatarSetupActions()
        )
    }

    @Test
    fun setupSelfieLimit() = compose.captureRunkkScreen("J7_avatar_face_selfie_limit") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                isLoading = false,
                canCreateSelfie = false,
                error = UiText.Res(R.string.avatar_error_selfie_limit)
            ),
            AvatarSetupActions()
        )
    }

    @Test
    fun decorate() = compose.captureRunkkScreen("J3_avatar_dress") {
        AvatarScreen(decorating(), AvatarScreenActions())
    }

    @Test
    fun shop() = compose.captureRunkkScreen("J4_shop") {
        ShopScreen(
            ShopUiState(isLoading = false, shop = Shop(items, 1_240), avatar = avatar, selected = items[2]),
            ShopActions()
        )
    }

    @Test
    fun shopConfirm() = compose.captureRunkkScreen("J5_shop_buy_confirm") {
        ShopScreen(ShopUiState(isLoading = false, shop = Shop(items, 1_240), confirming = items[2]), ShopActions())
    }

    @Test
    fun background() = compose.captureRunkkScreen("J3_avatar_dress_background") {
        AvatarScreen(
            decorating(isBackgroundTab = true),
            AvatarScreenActions(),
            background = AvatarBackgroundUiState(
                isLoading = false,
                backgrounds = sampleBackgrounds.copy(selectedKey = "INCHEON")
            )
        )
    }

    @Test
    fun decorateLight() = compose.captureRunkkScreen("J3_avatar_dress_light", isDarkTheme = false) {
        AvatarScreen(decorating().copy(avatar = avatar.copy(backgroundKey = "INCHEON")), AvatarScreenActions())
    }

    @Test
    fun backgroundLight() = compose.captureRunkkScreen("J3_avatar_dress_background_light", isDarkTheme = false) {
        AvatarScreen(
            decorating(isBackgroundTab = true),
            AvatarScreenActions(),
            background = AvatarBackgroundUiState(
                isLoading = false,
                backgrounds = sampleBackgrounds.copy(selectedKey = "INCHEON")
            )
        )
    }
}

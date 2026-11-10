package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

/** 아바타 화면: 주요 버튼·접근성 라벨, 셀카 줄이기. */
@RunWith(RobolectricTestRunner::class)
class AvatarScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    @Test
    fun `얼굴 만들기 - 캐릭터를 고르기 전에는 저장할 수 없고 셀카, 앨범 버튼이 있다(J7)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(
                    AvatarSetupUiState(isLoading = false),
                    onBack = {},
                    onSelfie = { calls += "selfie" },
                    onCharacter = { calls += it.name },
                    onSave = { calls += "save" },
                    onAlbum = { calls += "album" }
                )
            }
        }
        compose.onNodeWithText(str(R.string.avatar_setup_save)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.avatar_setup_selfie_daily)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_take_selfie)).performClick()
        compose.onNodeWithText(str(R.string.avatar_setup_pick_album)).performClick()
        compose.onNodeWithContentDescription(str(R.string.avatar_character_bear)).performClick()
        assertEquals(listOf("selfie", "album", "BEAR"), calls)
    }

    @Test
    fun `얼굴 만들기 - 서버가 얼굴을 만드는 중이면 알리고 셀카, 앨범을 막고 기본 캐릭터 저장은 된다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(
                    AvatarSetupUiState(isLoading = false, selected = DefaultCharacter.FOX, isMakingFace = true),
                    {},
                    {},
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithText(str(R.string.avatar_setup_making)).assertExists()
        compose.onNodeWithText(str(R.string.avatar_setup_take_selfie)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.avatar_setup_pick_album)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.avatar_setup_save)).assertIsEnabled()
    }

    @Test
    fun `얼굴 만들기 - 셀카 실패는 셀카 카드 안에 보이고 다시 시도를 누르면 사진을 고른다(DV-성-10)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(
                    AvatarSetupUiState(
                        isLoading = false,
                        hasFaceConsent = true,
                        error = UiText.Res(R.string.avatar_error_selfie_failed),
                        canRetrySelfie = true
                    ),
                    onBack = {},
                    onSelfie = {},
                    onCharacter = {},
                    onSave = {},
                    onAlbum = { calls += "album" }
                )
            }
        }
        val inCard = hasAnyAncestor(hasTestTag(SELFIE_CARD_TAG))
        compose.onNode(hasText(str(R.string.avatar_error_selfie_failed)) and inCard).assertExists()
        compose.onNode(hasText(str(R.string.avatar_setup_retry)) and inCard).performClick()
        assertEquals(listOf("album"), calls)
    }

    @Test
    fun `얼굴 만들기 - 셀카 카드의 동의 체크를 누르면 동의하고, 동의했으면 체크된 채 다시 눌러도 철회하지 않는다`() {
        var state by mutableStateOf(AvatarSetupUiState(isLoading = false))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(
                    state,
                    {},
                    {},
                    {},
                    {},
                    onFaceConsent = {
                        calls += "consent"
                        state = state.copy(hasFaceConsent = true)
                    }
                )
            }
        }
        val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        val consent = str(R.string.avatar_setup_face_consent)
        compose.onNodeWithText(consent, substring = true).assertExists()
        compose.onNode(isCheckbox).assertIsOff().performClick()
        compose.onNode(isCheckbox).assertIsOn().performClick()
        compose.onNode(isCheckbox).assertIsOn()
        assertEquals(listOf("consent"), calls)
    }

    @Test
    fun `꾸미기 - 아바타를 그림 하나로 읽어 주고 아이템을 누르면 입히고 상점, 얼굴, 저장으로 간다(J3)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(isLoading = false, avatar = avatar, items = items),
                    AvatarScreenActions(
                        onShop = { calls += "shop" },
                        onFace = { calls += "face" },
                        onToggle = { calls += "toggle:${it.itemId}" },
                        onBackgroundTab = { calls += "background" },
                        onSave = { calls += "save" }
                    )
                )
            }
        }
        compose.onNodeWithContentDescription(
            str(
                R.string.avatar_figure_description,
                str(R.string.avatar_face_default, str(R.string.avatar_character_fox)),
                str(R.string.avatar_body_3)
            ) + str(R.string.avatar_figure_equipped_format, str(R.string.avatar_slot_top), "라임 러닝 티")
        ).assertExists()
        compose.onNodeWithText("라벤더 싱글렛").performClick()
        compose.onNodeWithText(str(R.string.avatar_decorate_go_shop)).performClick()
        compose.onNodeWithText(str(R.string.avatar_decorate_change_face)).performClick()
        compose.onNodeWithText(str(R.string.avatar_tab_background)).performClick()
        compose.onNodeWithText(str(R.string.avatar_decorate_save)).performClick()
        assertEquals(listOf("toggle:2", "shop", "face", "background", "save"), calls)
    }

    @Test
    fun `꾸미기 - 서버가 기본 캐릭터 키(default)를 주면 셀카가 아니라 그 캐릭터 그림으로 그리고 읽어 준다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarFigure(
                    avatar.copy(faceKey = "default:BEAR", defaultCharacterId = "BEAR"),
                    equippedNames = emptyMap()
                )
            }
        }
        compose.onNodeWithTag(runkkAvatarCharacterTag("BEAR"), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(
            str(
                R.string.avatar_figure_description,
                str(R.string.avatar_face_default, str(R.string.avatar_character_bear)),
                str(R.string.avatar_body_3)
            )
        ).assertExists()
    }

    @Test
    fun `얼굴 만들기 - 기본 캐릭터 4종을 그림으로 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(AvatarSetupUiState(isLoading = false), {}, {}, {}, {})
            }
        }
        DefaultCharacter.entries.forEach {
            // 미리보기(고르기 전이면 기본 얼굴)와 고르기 칸에 함께 그릴 수 있다.
            compose.onAllNodesWithTag(runkkAvatarCharacterTag(it.name), useUnmergedTree = true).onFirst().assertExists()
        }
    }

    @Test
    fun `꾸미기 - 셀카 얼굴 그림이 있으면 부위를 겹쳐 그리고 그림 하나로 읽어 준다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarFigure(
                    avatar.copy(faceKey = "selfie:face/7/s1"),
                    equippedNames = emptyMap(),
                    faceLayers = faceLayers
                )
            }
        }
        compose.onNodeWithTag(FACE_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("SD", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            str(R.string.avatar_figure_description, str(R.string.avatar_face_selfie), str(R.string.avatar_body_3))
        ).assertExists()
    }

    @Test
    fun `꾸미기 - 부위 그림이 깨졌으면 자리 그림(SD)을 그린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarFigure(
                    avatar.copy(faceKey = "selfie:face/7/s1"),
                    equippedNames = emptyMap(),
                    faceLayers = listOf(
                        com.a207.runkk.app.core.model.FaceLayer(
                            com.a207.runkk.app.core.model.FacePart.EYES,
                            byteArrayOf(1, 2)
                        )
                    )
                )
            }
        }
        compose.onNodeWithText("SD", useUnmergedTree = true).assertExists()
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
        compose.onNodeWithText(str(R.string.avatar_shop_confirm_title, "런 캡")).assertExists()
        compose.onNodeWithText(
            str(R.string.avatar_shop_confirm_message, "${items[2].price}", "${1_240 - items[2].price}")
        )
            .assertExists()
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
    fun `체형, 피부톤 문구`() {
        assertEquals(3, bodyStage("STAGE3"))
        assertEquals(R.string.avatar_body_4, bodyLabelRes(4))
        assertEquals(UiText.Res(R.string.avatar_skin_morning), skinLabel("MORNING"))
        assertEquals(R.string.avatar_slot_hat, ItemSlot.HAT.labelRes)
    }

    @Test
    fun `배경 탭 - 칸은 라디오로 알리고 잠김 상태를 읽으며 누르면 고른다(J3, FR-201)`() {
        val chosen = mutableListOf<String>()
        var defaults = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(isLoading = false, avatar = avatar, items = items, isBackgroundTab = true),
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
}

/** 아바타 스크린샷(J3~J5, J7). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class AvatarScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun setup() = compose.captureRunkkScreen("J7_avatar_face") {
        AvatarSetupScreen(AvatarSetupUiState(isLoading = false, selected = DefaultCharacter.FOX), {}, {}, {}, {})
    }

    /** 셀카 접수 뒤 서버가 얼굴을 만드는 중(S15P31A207-248): 진행 원과 「얼굴을 만들고 있어요」, 셀카 꺼짐, 동의 체크됨. */
    @Test
    fun setupMakingFace() = compose.captureRunkkScreen("J7_avatar_face_making") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                isLoading = false,
                selected = DefaultCharacter.FOX,
                isMakingFace = true,
                hasFaceConsent = true
            ),
            {},
            {},
            {},
            {}
        )
    }

    /** 셀카 얼굴 생성 실패(DV-성-10): 실패 문구와 다시 시도가 셀카 카드 안에 있다. */
    @Test
    fun setupSelfieFailed() = compose.captureRunkkScreen("J7_avatar_face_failed") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                isLoading = false,
                hasFaceConsent = true,
                error = UiText.Res(R.string.avatar_error_selfie_failed),
                canRetrySelfie = true
            ),
            {},
            {},
            {},
            {}
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
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun decorate() = compose.captureRunkkScreen("J3_avatar_dress") {
        AvatarScreen(AvatarUiState(isLoading = false, avatar = avatar, items = items), AvatarScreenActions())
    }

    @Test
    fun shop() = compose.captureRunkkScreen("J4_shop") {
        ShopScreen(ShopUiState(isLoading = false, shop = Shop(items, 1_240), selected = items[2]), ShopActions())
    }

    @Test
    fun shopConfirm() = compose.captureRunkkScreen("J5_shop_buy_confirm") {
        ShopScreen(ShopUiState(isLoading = false, shop = Shop(items, 1_240), confirming = items[2]), ShopActions())
    }

    @Test
    fun background() = compose.captureRunkkScreen("J3_avatar_dress_background") {
        AvatarScreen(
            AvatarUiState(isLoading = false, avatar = avatar, items = items, isBackgroundTab = true),
            AvatarScreenActions(),
            background = AvatarBackgroundUiState(
                isLoading = false,
                backgrounds = sampleBackgrounds.copy(selectedKey = "INCHEON")
            )
        )
    }

    @Test
    fun decorateLight() = compose.captureRunkkScreen("J3_avatar_dress_light", isDarkTheme = false) {
        AvatarScreen(
            AvatarUiState(isLoading = false, avatar = avatar.copy(backgroundKey = "INCHEON"), items = items),
            AvatarScreenActions()
        )
    }

    @Test
    fun backgroundLight() = compose.captureRunkkScreen("J3_avatar_dress_background_light", isDarkTheme = false) {
        AvatarScreen(
            AvatarUiState(isLoading = false, avatar = avatar, items = items, isBackgroundTab = true),
            AvatarScreenActions(),
            background = AvatarBackgroundUiState(
                isLoading = false,
                backgrounds = sampleBackgrounds.copy(selectedKey = "INCHEON")
            )
        )
    }
}

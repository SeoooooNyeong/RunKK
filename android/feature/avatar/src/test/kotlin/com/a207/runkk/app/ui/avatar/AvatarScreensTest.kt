package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun `아바타 만들기 - 캐릭터를 고르기 전에는 시작할 수 없고 셀카·나중에 버튼이 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(
                    AvatarSetupUiState(isLoading = false),
                    onBack = {},
                    onSelfie = { calls += "selfie" },
                    onCharacter = { calls += it.name },
                    onConfirm = {},
                    onLater = { calls += "later" }
                )
            }
        }
        compose.onNodeWithText(str(R.string.avatar_setup_pick_prompt)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.avatar_setup_take_selfie)).performClick()
        compose.onNodeWithText(str(R.string.avatar_character_bear)).performClick()
        compose.onNodeWithText(str(R.string.avatar_setup_later)).performClick()
        assertEquals(listOf("selfie", "BEAR", "later"), calls)
    }

    @Test
    fun `꾸미기 - 아바타를 그림 하나로 읽어 주고 아이템을 누르면 입힌다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(isLoading = false, avatar = avatar, items = items),
                    onBack = {},
                    onShop = { calls += "shop" },
                    onFace = { calls += "face" },
                    onSlot = {},
                    onToggle = { calls += "toggle:${it.itemId}" },
                    onRetry = {},
                    onMessageShown = {}
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
        compose.onNodeWithContentDescription(str(R.string.avatar_balance_open_shop, "1,240")).performClick()
        compose.onNodeWithText(str(R.string.avatar_decorate_change_face)).performClick()
        assertEquals(listOf("toggle:2", "shop", "face"), calls)
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
    fun `아바타 만들기 - 기본 캐릭터 4종을 그림으로 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarSetupScreen(AvatarSetupUiState(isLoading = false), {}, {}, {}, {}, {})
            }
        }
        DefaultCharacter.entries.forEach {
            compose.onNodeWithTag(runkkAvatarCharacterTag(it.name), useUnmergedTree = true).assertExists()
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
    fun `상점 - 사기 전에 확인을 받는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ShopScreen(
                    ShopUiState(isLoading = false, shop = Shop(items, 1_240), confirming = items[2]),
                    onBack = {},
                    onSlot = {},
                    onPick = {},
                    onBuy = { calls += "buy" },
                    onDismiss = {},
                    onRetry = {},
                    onMessageShown = {}
                )
            }
        }
        compose.onNodeWithText(str(R.string.avatar_shop_confirm_title, "런 캡")).assertExists()
        compose.onNodeWithText(str(R.string.avatar_shop_buy)).performClick()
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
    fun `체형·피부톤 문구`() {
        assertEquals(3, bodyStage("STAGE3"))
        assertEquals(R.string.avatar_body_4, bodyLabelRes(4))
        assertEquals(UiText.Res(R.string.avatar_skin_morning), skinLabel("MORNING"))
        assertEquals(R.string.avatar_slot_hat, ItemSlot.HAT.labelRes)
    }
}

/** 아바타 스크린샷(F1~F3). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class AvatarScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun setup() = compose.captureRunkkScreen("F1_avatar_setup") {
        AvatarSetupScreen(AvatarSetupUiState(isLoading = false, selected = DefaultCharacter.FOX), {}, {}, {}, {}, {})
    }

    @Test
    fun setupSelfieLimit() = compose.captureRunkkScreen("F1_avatar_setup_selfie_limit") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                isLoading = false,
                canCreateSelfie = false,
                error = UiText.Res(R.string.avatar_error_selfie_limit)
            ),
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test
    fun decorate() = compose.captureRunkkScreen("F2_avatar_decorate") {
        AvatarScreen(AvatarUiState(isLoading = false, avatar = avatar, items = items), {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun shop() = compose.captureRunkkScreen("F3_shop") {
        ShopScreen(ShopUiState(isLoading = false, shop = Shop(items, 1_240)), {}, {}, {}, {}, {}, {}, {})
    }
}

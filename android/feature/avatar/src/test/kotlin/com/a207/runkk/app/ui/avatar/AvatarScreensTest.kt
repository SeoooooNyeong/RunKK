package com.a207.runkk.app.ui.avatar

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.RunkkTheme
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
import org.robolectric.annotation.Config

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
                    AvatarSetupUiState(loading = false),
                    onBack = {},
                    onSelfie = { calls += "selfie" },
                    onCharacter = { calls += it.name },
                    onConfirm = {},
                    onLater = { calls += "later" }
                )
            }
        }
        compose.onNodeWithText("캐릭터를 골라 주세요").assertIsNotEnabled()
        compose.onNodeWithText("셀카 찍기").performClick()
        compose.onNodeWithText("곰").performClick()
        compose.onNodeWithText("나중에 할게요").performClick()
        assertEquals(listOf("selfie", "BEAR", "later"), calls)
    }

    @Test
    fun `꾸미기 - 아바타를 그림 하나로 읽어 주고 아이템을 누르면 입힌다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                AvatarScreen(
                    AvatarUiState(loading = false, avatar = avatar, items = items),
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
        compose.onNodeWithContentDescription("아바타, 기본 캐릭터 · 여우, 체형 탄탄해지는 중, 상의 라임 러닝 티").assertExists()
        compose.onNodeWithText("라벤더 싱글렛").performClick()
        compose.onNodeWithContentDescription("리워드 잔액 1,240, 상점 열기").performClick()
        compose.onNodeWithText("얼굴 바꾸기").performClick()
        assertEquals(listOf("toggle:2", "shop", "face"), calls)
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
        compose.onNodeWithContentDescription("아바타, 셀카 SD 얼굴, 체형 탄탄해지는 중").assertExists()
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
                    ShopUiState(loading = false, shop = Shop(items, 1_240), confirming = items[2]),
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
        compose.onNodeWithText("런 캡을(를) 살까요?").assertExists()
        compose.onNodeWithText("사기").performClick()
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
        assertEquals("근육 탄탄", bodyLabel(4))
        assertTrue(skinLabel("MORNING").startsWith("아침 톤"))
        assertEquals("모자", ItemSlot.HAT.label)
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
        AvatarSetupScreen(AvatarSetupUiState(loading = false, selected = DefaultCharacter.FOX), {}, {}, {}, {}, {})
    }

    @Test
    fun setupSelfieLimit() = compose.captureRunkkScreen("F1_avatar_setup_selfie_limit") {
        AvatarSetupScreen(
            AvatarSetupUiState(
                loading = false,
                canCreateSelfie = false,
                error = "오늘은 이미 셀카 아바타를 만들었어요. 내일 다시 만들 수 있어요. 기본 캐릭터는 지금 고를 수 있어요"
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
        AvatarScreen(AvatarUiState(loading = false, avatar = avatar, items = items), {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun shop() = compose.captureRunkkScreen("F3_shop") {
        ShopScreen(ShopUiState(loading = false, shop = Shop(items, 1_240)), {}, {}, {}, {}, {}, {}, {})
    }
}

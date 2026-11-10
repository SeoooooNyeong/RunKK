package com.a207.runkk.app.ui.avatar

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

/** 실기기 3차(갤럭시 S22, 2026-11-11) 화면 결함 DV3-5·6·7·10. */
@RunWith(RobolectricTestRunner::class)
class DeviceRound3Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `DV3-6 을·를은 마지막 글자 받침으로 고른다`() {
        assertEquals("를", objectParticle("새벽 바람막이"))
        assertEquals("을", objectParticle("트레일 러닝화 한정판"))
        assertEquals("을", objectParticle("러닝 캡"))
        assertEquals("를", objectParticle("헤어밴드"))
        assertEquals("를", objectParticle("Cap"), "한글이 아니면 받침 없음으로 본다")
    }

    @Test
    fun `DV3-6 구매 확인 제목은 받침에 맞는 조사다`() {
        val item = Shop.Item(2, ItemSlot.TOP, "새벽 바람막이", 500, false, true)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ShopScreen(
                    ShopUiState(isLoading = false, shop = Shop(listOf(item), 1_240), confirming = item),
                    ShopActions()
                )
            }
        }
        compose.onNodeWithText("새벽 바람막이를 살까요?").assertExists()
        compose.onNodeWithText("을(를)", substring = true).assertDoesNotExist()
    }

    @Test
    fun `DV3-5 상점 넷째 탭은 꾸미기와 같이 악세서리다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ShopScreen(ShopUiState(isLoading = false, shop = Shop(items, 1_240)), ShopActions())
            }
        }
        compose.onNodeWithText(text(R.string.avatar_slot_accessory)).assertExists()
        compose.onNodeWithText(text(R.string.avatar_slot_hat)).assertDoesNotExist()
    }

    @Test
    fun `DV3-7 꾸미기에서 바뀐 것이 없으면 저장은 비활성 단추이고 바꾸면 켜진다`() {
        var state by mutableStateOf(
            AvatarUiState(isLoading = false, avatar = avatar, items = items, draft = avatar.equipped)
        )
        compose.setContent { RunkkTheme(reduceMotion = true) { AvatarScreen(state, AvatarScreenActions()) } }
        compose.onNodeWithText(text(R.string.avatar_decorate_save)).assertIsNotEnabled()
        state = state.copy(draft = mapOf(ItemSlot.TOP to 2L))
        compose.onNodeWithText(text(R.string.avatar_decorate_save)).assertIsEnabled()
    }

    @Test
    fun `DV3-10 Rive 아바타를 보일 때는 기본 얼굴 이름을 읽지 않는다(기본 그림일 때만)`() {
        val state =
            AvatarUiState(
                isLoading = false,
                avatar = avatar.copy(faceKey = "default:RABBIT"),
                items = items,
                draft = avatar.equipped
            )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Text(figureDescription(state.avatar!!, state, isFallback = false))
            }
        }
        compose.onNodeWithText(text(R.string.avatar_character_rabbit), substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.avatar_figure_rive), substring = true).assertExists()
    }

    @Test
    fun `DV3-10 기본 그림(Rive 실패)이면 기본 얼굴 이름을 읽는다`() {
        val state =
            AvatarUiState(
                isLoading = false,
                avatar = avatar.copy(faceKey = "default:RABBIT"),
                items = items,
                draft = avatar.equipped
            )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Text(figureDescription(state.avatar!!, state, isFallback = true))
            }
        }
        compose.onNodeWithText(text(R.string.avatar_character_rabbit), substring = true).assertExists()
    }
}

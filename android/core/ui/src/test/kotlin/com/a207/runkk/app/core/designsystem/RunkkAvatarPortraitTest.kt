package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.testing.ContractEnums
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 아바타 얼굴 키(서버 Face.renderKey, 설계 6.1.8 avatar): "default:<DefaultCharacter>"는 앱에 든 기본 캐릭터 그림으로,
 * 셀카 얼굴("selfie:…")은 부위 그림으로, 모르는 값은 자리 그림으로 그린다.
 */
@RunWith(RobolectricTestRunner::class)
class RunkkAvatarPortraitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `기본 캐릭터 키는 캐릭터마다 그림을 고른다`() {
        assertEquals(R.drawable.avatar_character_rabbit, RunkkAvatarArt.characterOf("default:RABBIT"))
        assertEquals(R.drawable.avatar_character_bear, RunkkAvatarArt.characterOf("default:BEAR"))
        assertEquals(R.drawable.avatar_character_fox, RunkkAvatarArt.characterOf("default:FOX"))
        assertEquals(R.drawable.avatar_character_cat, RunkkAvatarArt.characterOf("default:CAT"))
    }

    @Test
    fun `서버 DefaultCharacter마다 그림이 하나씩 있다`() {
        val server = ContractEnums.names("DefaultCharacter")
        assertEquals(server, RunkkAvatarArt.characters.keys)
        assertEquals(server.size, RunkkAvatarArt.characters.values.toSet().size, "캐릭터마다 다른 그림")
    }

    @Test
    fun `셀카 얼굴·모르는 값·없음은 기본 캐릭터 그림이 아니다`() {
        assertNull(RunkkAvatarArt.characterOf("selfie:face/1/set-ab12"))
        assertNull(RunkkAvatarArt.characterOf("default:DRAGON"))
        assertNull(RunkkAvatarArt.characterOf("BEAR"))
        assertNull(RunkkAvatarArt.characterOf(""))
        assertNull(RunkkAvatarArt.characterOf(null))
        assertEquals("default:FOX", RunkkAvatarArt.defaultKey("FOX"))
    }

    @Test
    fun `기본 캐릭터 키면 그 캐릭터 그림을 그린다`() {
        show("default:BEAR", emptyList())
        compose.onNodeWithTag(runkkAvatarCharacterTag("BEAR"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(PLACEHOLDER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `셀카 얼굴이면 부위 그림을 겹쳐 그리고 캐릭터 그림은 그리지 않는다`() {
        show("selfie:face/1/s1", FacePart.entries.map { FaceLayer(it, TINY_PNG) })
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(PLACEHOLDER, useUnmergedTree = true).assertDoesNotExist()
        RunkkAvatarArt.characters.keys.forEach {
            compose.onNodeWithTag(runkkAvatarCharacterTag(it), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `모르는 값이면 자리 그림을 그린다`() {
        show("default:DRAGON", emptyList())
        compose.onNodeWithText(PLACEHOLDER, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `셀카 그림을 받지 못했으면 자리 그림을 그린다`() {
        show("selfie:face/1/s1", emptyList())
        compose.onNodeWithText(PLACEHOLDER, useUnmergedTree = true).assertExists()
    }

    private fun show(faceKey: String?, layers: List<FaceLayer>) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatarPortrait(faceKey, layers, Modifier.size(56.dp)) {
                    Text(PLACEHOLDER)
                }
            }
        }
    }

    private companion object {
        const val PLACEHOLDER = "자리"

        /** 1×1 PNG(부위 그림 표본). */
        val TINY_PNG: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )
    }
}

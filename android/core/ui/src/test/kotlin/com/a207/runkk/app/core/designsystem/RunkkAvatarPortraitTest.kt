package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 프로필 얼굴 = 아바타 머리(design-oo 05-30 [임시:D-성-180], [임시:D-성-187]): 셀카 얼굴("selfie:…", "head:…")이면 받은 부위 그림을
 * 겹치고, 얼굴 키가 없거나 어느 기본 캐릭터 값("default:*")이든 표시 성별의 기본 머리를 그린다. 동물 그림은 그리지 않는다. 셀카 그림을
 * 받기 전·받지 못했을 때도 기본 머리다.
 */
@RunWith(RobolectricTestRunner::class)
class RunkkAvatarPortraitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `기본 머리 그림은 성별마다 하나다`() {
        assertEquals(R.drawable.avatar_default_head_male, RunkkAvatarArt.defaultHeadOf(AvatarCharacter.MALE))
        assertEquals(R.drawable.avatar_default_head_female, RunkkAvatarArt.defaultHeadOf(AvatarCharacter.FEMALE))
        assertNotEquals(defaultHeadTag(AvatarCharacter.MALE), defaultHeadTag(AvatarCharacter.FEMALE))
    }

    @Test
    fun `기본 캐릭터 키는 어느 값이든 기본 얼굴이고 셀카·없음·모르는 형식은 아니다`() {
        listOf("default:RABBIT", "default:BEAR", "default:FOX", "default:CAT", "default:DRAGON").forEach {
            assertTrue(RunkkAvatarArt.isDefaultKey(it), it)
        }
        listOf("selfie:face/1/set-ab12", "head:7:job-1", "BEAR", "", null).forEach {
            assertFalse(RunkkAvatarArt.isDefaultKey(it), "$it")
        }
        assertEquals("default:FOX", RunkkAvatarArt.defaultKey("FOX"))
        assertEquals("default:RABBIT", RunkkAvatarArt.DEFAULT_FACE_KEY)
    }

    @Test
    fun `기본 캐릭터 키면 동물 그림 대신 표시 성별의 기본 머리를 그린다`() {
        show("default:BEAR", emptyList(), AvatarCharacter.FEMALE)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `얼굴 키가 없으면 기본 머리다`() {
        show(null, emptyList(), AvatarCharacter.MALE)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `성별을 넘기지 않으면 내 성별(LocalAvatarCharacter)의 기본 머리다`() {
        compose.setContent {
            CompositionLocalProvider(LocalAvatarCharacter provides AvatarCharacter.FEMALE) {
                RunkkTheme(reduceMotion = true) { RunkkAvatarPortrait(null, emptyList(), Modifier.size(56.dp)) }
            }
        }
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `셀카 얼굴이면 부위 그림을 겹쳐 그리고 기본 머리는 그리지 않는다`() {
        show("selfie:face/1/s1", FacePart.entries.map { FaceLayer(it, TINY_PNG) }, AvatarCharacter.MALE)
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `셀카 그림을 아직 받지 못했으면 빈 칸 대신 기본 머리다`() {
        show("selfie:face/1/s1", emptyList(), AvatarCharacter.FEMALE)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `셀카 그림이 깨졌으면 기본 머리다`() {
        show("selfie:face/1/s1", listOf(FaceLayer(FacePart.HAIR, byteArrayOf(1, 2, 3))), AvatarCharacter.MALE)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `셀카 머리 얼굴(head)은 받은 층을 겹쳐 그리고, 아직 못 받았으면 기본 머리다`() {
        var layers by mutableStateOf(emptyList<FaceLayer>())
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatar("head:12:job-1", AvatarExpression.FACE, Modifier.size(40.dp), layers)
            }
        }
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertExists()
        layers = listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR).map { FaceLayer(it, TINY_PNG) }
        compose.waitForIdle()
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `다른 러너 얼굴 원은 넘긴 표시 성별의 기본 머리다`() {
        compose.setContent {
            CompositionLocalProvider(LocalAvatarCharacter provides AvatarCharacter.MALE) {
                RunkkTheme(reduceMotion = true) {
                    RunkkAvatar(
                        "default:CAT",
                        AvatarExpression.FACE,
                        Modifier.size(40.dp),
                        character = AvatarCharacter.FEMALE
                    )
                }
            }
        }
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `결과 상세 아바타는 넘긴 내 아바타 얼굴을 그린다(F-16b)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunResultDetail(
                    RunResultDetailState(
                        com.a207.runkk.app.core.model.Distance(4_870),
                        "",
                        java.time.Duration.ofMinutes(28),
                        faceKey = "default:FOX"
                    )
                )
            }
        }
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertExists()
    }

    private fun defaultHeadTag(character: AvatarCharacter): String = runkkAvatarDefaultHeadTag(character)

    private fun show(faceKey: String?, layers: List<FaceLayer>, character: AvatarCharacter) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatarPortrait(faceKey, layers, Modifier.size(56.dp), character)
            }
        }
    }

    private companion object {
        /** 1×1 PNG(부위 그림 표본). */
        val TINY_PNG: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )
    }
}

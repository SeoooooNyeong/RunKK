package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * 받는 중이면 중립 자리(기본 머리를 거치지 않는다, [임시:D-성-190]), 받지 못했거나 깨졌으면 기본 머리다.
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
    fun `셀카 키이고 그림을 받는 중이면 기본 머리 대신 중립 자리다(임시 D-성-190)`() {
        show("head:7:job-1", emptyList(), AvatarCharacter.FEMALE, isFaceLoading = true)
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `얼굴 키를 아직 모르는 채 읽는 중이면(프로필 읽기 전) 기본 머리 대신 중립 자리다`() {
        show(null, emptyList(), AvatarCharacter.MALE, isFaceLoading = true)
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `기본 캐릭터 키면 읽는 중이어도 기다리지 않고 기본 머리다`() {
        show("default:RABBIT", emptyList(), AvatarCharacter.MALE, isFaceLoading = true)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `셀카 그림을 받지 못하고 끝났으면(받기 실패) 기본 머리다`() {
        show("selfie:face/1/s1", emptyList(), AvatarCharacter.FEMALE)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `셀카 그림이 깨졌으면 기본 머리다`() {
        show("selfie:face/1/s1", listOf(FaceLayer(FacePart.HAIR, byteArrayOf(1, 2, 3))), AvatarCharacter.MALE)
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `셀카 머리 얼굴(head)은 받는 동안 중립 자리이고 기본 머리를 거치지 않고 받은 층으로 바뀐다(임시 D-성-190)`() {
        var layers by mutableStateOf(emptyList<FaceLayer>())
        var loading by mutableStateOf(true)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatar(
                    "head:12:job-1",
                    AvatarExpression.FACE,
                    Modifier.size(40.dp),
                    layers,
                    isFaceLoading = loading
                )
            }
        }
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_LOADING_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(defaultHeadTag(AvatarCharacter.MALE), useUnmergedTree = true).assertDoesNotExist()
        layers = listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR).map { FaceLayer(it, TINY_PNG) }
        loading = false
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
        // 결과 상세는 얼굴 원이 아닌 장면이라 원 밖 기본 머리다([임시:D-성-193]).
        compose.onNodeWithTag(
            runkkAvatarDefaultHeadTag(AvatarCharacter.MALE, isInCircle = false),
            useUnmergedTree = true
        )
            .assertExists()
    }

    @Test
    fun `기본 머리 그림은 프로필 원 목표 구도다 - 머리가 크고 정수리가 위 끝에 닿으며 목이 아래 끝까지 이어진다(임시 D-성-189)`() {
        val resources = org.robolectric.RuntimeEnvironment.getApplication().resources
        AvatarCharacter.entries.forEach { character ->
            val bitmap = android.graphics.BitmapFactory.decodeResource(
                resources,
                RunkkAvatarArt.defaultHeadOf(character)
            )
            val size = bitmap.width
            fun alpha(x: Int, y: Int) = android.graphics.Color.alpha(bitmap.getPixel(x, y))
            fun span(y: Int): Int {
                var left = size / 2
                var right = size / 2
                while (left > 0 && alpha(left - 1, y) >= 128) left--
                while (right < size - 1 && alpha(right + 1, y) >= 128) right++
                return right - left + 1
            }
            val crown = (0 until size).first { y -> (0 until size).any { alpha(it, y) >= 128 } }
            assertTrue(crown <= size * .02f, "$character 정수리가 위 끝에 닿는다: $crown")
            assertTrue(alpha(size / 2, size - 1) >= 200, "$character 목이 아래 끝까지 이어진다")
            assertTrue(span(size - 1) < size * .3f, "$character 아래 끝은 목 폭이다: ${span(size - 1)}")
            val widest = (size * 15 / 100 until size * 7 / 10).maxOf { y ->
                (0 until size).count { alpha(it, y) >= 128 }
            }
            assertTrue(widest >= size * .85f, "$character 머리 너비가 원 지름의 약 90퍼센트다: $widest")
            bitmap.recycle()
        }
    }

    @Test
    fun `원 밖 그림(얼굴 원이 아닌 장면, 지도 마커)은 목을 늘리지 않은 기본 머리(_free)를 쓴다(임시 D-성-193)`() {
        assertEquals(
            R.drawable.avatar_default_head_male_free,
            RunkkAvatarArt.defaultHeadOf(AvatarCharacter.MALE, false)
        )
        assertEquals(
            R.drawable.avatar_default_head_female_free,
            RunkkAvatarArt.defaultHeadOf(AvatarCharacter.FEMALE, false)
        )
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    RunkkAvatar(null, AvatarExpression.STAND, Modifier.size(40.dp))
                    RunkkAvatar(null, AvatarExpression.FACE, Modifier.size(40.dp))
                    RunkkAvatar(null, AvatarExpression.FACE, Modifier.size(40.dp), isInCircle = false)
                }
            }
        }
        compose.onAllNodesWithTag(runkkAvatarDefaultHeadTag(AvatarCharacter.MALE, isInCircle = false), true)
            .assertCountEquals(2)
        compose.onAllNodesWithTag(runkkAvatarDefaultHeadTag(AvatarCharacter.MALE), true).assertCountEquals(1)
    }

    @Test
    fun `원 밖 기본 머리 그림은 같은 구도에 목이 제 길이로 둥글게 끝나 아래 끝에 닿지 않는다`() {
        val resources = org.robolectric.RuntimeEnvironment.getApplication().resources
        AvatarCharacter.entries.forEach { character ->
            val bitmap = android.graphics.BitmapFactory.decodeResource(
                resources,
                RunkkAvatarArt.defaultHeadOf(character, isInCircle = false)
            )
            val size = bitmap.width
            fun alpha(x: Int, y: Int) = android.graphics.Color.alpha(bitmap.getPixel(x, y))
            val crown = (0 until size).first { y -> (0 until size).any { alpha(it, y) >= 128 } }
            assertTrue(crown <= size * .02f, "$character 정수리는 원 그림과 같은 자리")
            assertTrue(alpha(size / 2, size * 88 / 100) >= 200, "$character 턱 아래 목은 있다")
            assertTrue(alpha(size / 2, size - 1) < 50, "$character 목은 아래 끝까지 늘리지 않는다")
            bitmap.recycle()
        }
    }

    @Test
    fun `원 밖 셀카 머리는 목이 아래 끝에 닿지 않는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatarPortrait(
                    "head:1:job-1",
                    SelfieHeadFixture.layers,
                    Modifier.size(100.dp).testTag("portrait"),
                    AvatarCharacter.MALE,
                    isInCircle = false
                )
            }
        }
        val image = compose.onNodeWithTag("portrait").captureToImage().asAndroidBitmap()
        // 캡처는 창 바탕까지 찍으므로 아래 끝 가운데가 바탕(왼쪽 위 모서리)과 같은 색이면 목이 닿지 않은 것이다.
        assertEquals(image.getPixel(1, 1), image.getPixel(image.width / 2, image.height - 2), "아래 끝 가운데는 바탕")
    }

    private fun defaultHeadTag(character: AvatarCharacter): String = runkkAvatarDefaultHeadTag(character)

    private fun show(
        faceKey: String?,
        layers: List<FaceLayer>,
        character: AvatarCharacter,
        isFaceLoading: Boolean = false
    ) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatarPortrait(faceKey, layers, Modifier.size(56.dp), character, isFaceLoading)
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

package com.a207.runkk.app.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.runkkAvatarDefaultHeadTag
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.domain.chat.ReactionKind
import com.a207.runkk.app.domain.chat.Reactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReactorGenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `내 성별이 여자이면 남자 표시 성별이어도 내 기본 얼굴은 여자다`() =
        assertGender(AvatarCharacter.FEMALE, Gender.MALE, isMe = true, AvatarCharacter.FEMALE)

    @Test
    fun `내 성별이 남자이면 여자 표시 성별이어도 내 기본 얼굴은 남자다`() =
        assertGender(AvatarCharacter.MALE, Gender.FEMALE, isMe = true, AvatarCharacter.MALE)

    @Test
    fun `내 성별이 여자여도 남의 남자 표시 성별을 바꾸지 않는다`() =
        assertGender(AvatarCharacter.FEMALE, Gender.MALE, isMe = false, AvatarCharacter.MALE)

    @Test
    fun `내 성별이 남자여도 남의 여자 표시 성별을 바꾸지 않는다`() =
        assertGender(AvatarCharacter.MALE, Gender.FEMALE, isMe = false, AvatarCharacter.FEMALE)

    private fun assertGender(local: AvatarCharacter, shown: Gender, isMe: Boolean, expected: AvatarCharacter) {
        var failedFace by mutableStateOf(false)
        val target = chatMessage(101)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalAvatarCharacter provides local) {
                    ReactorsSheet(
                        ChatUiState(
                            reactorsTarget = target,
                            reactors = listOf(
                                Reactor(
                                    1,
                                    "러너",
                                    ReactionKind.LIKE,
                                    T0,
                                    isMe = isMe,
                                    shownGender = shown,
                                    faceKey = if (failedFace) "selfie:1/broken" else null
                                )
                            ),
                            faces = if (failedFace) {
                                mapOf("selfie:1/broken" to listOf(FaceLayer(FacePart.EYES, byteArrayOf(1))))
                            } else {
                                emptyMap()
                            }
                        ),
                        ChatActions()
                    )
                }
            }
        }
        // 얼굴 없음과 디코딩 실패를 같은 줄에서 각각 확인한다.
        repeat(2) {
            compose.onAllNodesWithTag(runkkAvatarDefaultHeadTag(expected), useUnmergedTree = true).assertCountEquals(1)
            val opposite = if (expected == AvatarCharacter.FEMALE) AvatarCharacter.MALE else AvatarCharacter.FEMALE
            compose.onAllNodesWithTag(runkkAvatarDefaultHeadTag(opposite), useUnmergedTree = true).assertCountEquals(0)
            compose.runOnIdle { failedFace = true }
        }
    }
}

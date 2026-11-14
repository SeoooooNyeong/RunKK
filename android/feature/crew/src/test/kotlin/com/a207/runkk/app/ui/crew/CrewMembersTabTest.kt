package com.a207.runkk.app.ui.crew

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.RUNKK_AVATAR_FACE_TAG
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.runkkAvatarDefaultHeadTag
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 크루원 목록 줄과 크루원 정보 팝업의 아바타 얼굴([임시:FR-성-24], [임시:D-성-235], 실기기 수정 요청 ①). 고치기 전에는 줄의 원이 빈 자리
 * (`RunkkAvatarCircle(PERSON_AVATAR)`)라 얼굴 표지가 없어 실패한다(원인 확인 시험).
 */
@RunWith(RobolectricTestRunner::class)
class CrewMembersTabTest {
    @get:Rule
    val compose = createComposeRule()

    private fun member(
        id: Long,
        faceKey: String?,
        shownGender: Gender?,
        runimalType: String? = null,
        level: Int? = null
    ) = CrewMembers.Member(
        memberId = id,
        displayName = "러너$id",
        role = CrewRole.MEMBER,
        crewIntro = null,
        tier = null,
        runimalType = runimalType,
        accumulated = Distance(1_000 * id),
        level = level,
        faceKey = faceKey,
        shownGender = shownGender
    )

    private fun state(vararg members: CrewMembers.Member) =
        CrewHomeUiState(crewId = 11, page = CrewHomePage.MEMBERS, members = CrewMembers(members.toList()))

    private fun show(state: CrewHomeUiState) = compose.setContent {
        RunkkTheme(reduceMotion = true) { CrewMembersPage(state, CrewHomeActions()) }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true)

    @Test
    fun 크루원_행은_faceKey가_있으면_얼굴을_그린다() {
        show(state(member(1, "default:FOX", Gender.FEMALE)))

        count(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE)).assertCountEquals(1)
    }

    @Test
    fun 셀카_얼굴_그림을_받은_크루원은_그_얼굴이고_못_받았으면_표시성별_기본_얼굴이다() {
        val layers = listOf(FaceLayer(FacePart.EYES, byteArrayOf(1)))
        show(
            state(member(1, "selfie:1/a", Gender.MALE), member(2, "selfie:2/b", Gender.FEMALE))
                .copy(memberFaces = mapOf("selfie:1/a" to layers))
        )

        count(RUNKK_AVATAR_FACE_TAG).assertCountEquals(1)
        count(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE)).assertCountEquals(1)
        compose.onAllNodesWithText("불러오지 못했어요", substring = true).assertCountEquals(0)
    }

    @Test
    fun faceKey가_없으면_표시성별의_기본_얼굴이다() {
        show(state(member(1, null, Gender.FEMALE), member(2, null, Gender.MALE), member(3, null, null)))

        count(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE)).assertCountEquals(1)
        // 표시 성별을 모르면(옛 서버) 남자 기본 얼굴이다(AvatarCharacter.of).
        count(runkkAvatarDefaultHeadTag(AvatarCharacter.MALE)).assertCountEquals(2)
    }

    @Test
    fun 이름_아래_메타_줄은_가운뎃점_없이_아이콘_항목으로_러니멀_유형과_레벨을_보인다() {
        show(state(member(1, null, Gender.MALE, runimalType = "MORNING_SPARROW", level = 26), member(2, null, null)))

        compose.onNodeWithContentDescription("아침 참새, Lv26", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("·", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun 크루원_정보_팝업의_80_원에도_그_크루원의_얼굴을_그린다() {
        val opened = member(2, "default:FOX", Gender.FEMALE)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CrewInfoDialogs(state(member(1, null, Gender.MALE)).copy(openedMember = opened), CrewHomeActions())
            }
        }

        count(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE)).assertCountEquals(1)
    }
}

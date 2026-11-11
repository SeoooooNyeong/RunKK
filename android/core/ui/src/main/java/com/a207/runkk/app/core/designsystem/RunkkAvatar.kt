package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 아바타 한 칸(D-254). 아바타 꾸미기(AVATAR_CUSTOMIZE)가 닫혀 있으면 [faceKey]를 무시하고 기본 아바타의 [expression] 그림을 그리고,
 * 열려 있으면 아바타 머리 그림([RunkkAvatarPortrait], [임시:D-성-180])을 쓴다. 홈·마이·결과·랭킹 행·매칭 성공 등이 모두 이 하나를 부른다.
 * 장식이므로 읽기 정보는 부르는 쪽이 붙인다. 기본 그림·기본 머리의 남녀는 [character]다: 내 그림이면 기본값(내 성별), 다른 러너면 그 러너의
 * 표시 성별(`AvatarCharacter.of(shownGender)`, D-327)을 넘긴다. [isFaceLoading]은 열린 배포의 머리 그림이 받는 중 자리를 둘 때 쓴다
 * ([RunkkAvatarPortrait], [임시:D-성-190]).
 */
@Composable
fun RunkkAvatar(
    faceKey: String?,
    expression: AvatarExpression,
    modifier: Modifier = Modifier,
    faceLayers: List<FaceLayer> = emptyList(),
    colorFilter: ColorFilter? = null,
    character: AvatarCharacter = LocalAvatarCharacter.current,
    isFaceLoading: Boolean = false
) {
    if (LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)) {
        // 아바타 머리([임시:D-성-180]): 셀카 머리면 받은 층, 받는 중이면 중립 자리, 얼굴 없음·기본 캐릭터 값·셀카 그림을 받지 못함이면
        // [character]의 기본 머리를 그린다(동물 그림 없음, [임시:D-성-190]).
        RunkkAvatarPortrait(faceKey, faceLayers, modifier, character, isFaceLoading)
    } else {
        val isFace = expression == AvatarExpression.FACE
        // 여자 서기·기쁨은 남자와 같은 짧은 체형으로 다시 그렸다(사용자 요청 2026-10-31). 아직 머리가 작고 다리가 긴 점프·달리기
        // 그림만 같은 키로 그리면 남자보다 커 보여 발을 붙인 채 조금 줄인다. 얼굴(프로필 원)·슬픔·찾기는 그대로다.
        val scale = if (character == AvatarCharacter.FEMALE &&
            expression in FEMALE_TALL_POSES
        ) {
            FEMALE_FIGURE_SCALE
        } else {
            1f
        }
        Image(
            painterResource(defaultAvatarOf(expression, character)),
            contentDescription = null,
            contentScale = if (isFace) ContentScale.Crop else ContentScale.Fit,
            // 전신 그림은 칸 바닥에 발을 붙인다(가운데 맞춤이면 발밑이 떠 보인다). 얼굴은 가운데
            alignment = if (isFace) Alignment.Center else Alignment.BottomCenter,
            colorFilter = colorFilter,
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = FEET
                }
                .testTag(DEFAULT_AVATAR_TAG + expression.name)
        )
    }
}

/** 아직 긴 체형인 여자 전신 그림([FEMALE_TALL_POSES])을 남자와 같은 칸에 그릴 때의 크기 비. */
internal const val FEMALE_FIGURE_SCALE = 0.9f

/** 남자와 체형이 다른(머리가 작고 다리가 긴) 여자 그림 자세. 서기·기쁨은 짧은 체형으로 다시 그려 빠졌다. */
private val FEMALE_TALL_POSES = setOf(AvatarExpression.JUMP, AvatarExpression.RUN)

/** 전신 그림을 줄일 때 기준점(칸 아래 가운데, 발밑). */
private val FEET = TransformOrigin(0.5f, 1f)

/**
 * 배틀런 상대 그림(피그마 C-3의 기본 아바타). 상대의 아바타를 받지 않으므로 상대 성별([character])로 남녀 기본 그림만 가른다
 * (사용자 결정 2026-10-31, 모르면 남자). 피그마 결과 화면(C-7)의 3D SD 남자 캐릭터는 잘못 들어간 것이라 쓰지 않는다
 * (사용자 확인 2026-10-30). 장식이다.
 */
@Composable
fun RivalAvatar(
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    character: AvatarCharacter = AvatarCharacter.MALE
) {
    Image(
        painterResource(
            if (character == AvatarCharacter.FEMALE) {
                defaultAvatarOf(AvatarExpression.STAND, character)
            } else {
                R.drawable.avatar_rival_stand
            }
        ),
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = modifier.testTag(RIVAL_AVATAR_TAG)
    )
}

/** 배틀런 상대 그림의 시험 표지. */
const val RIVAL_AVATAR_TAG = "rival-avatar"

/** 기본 아바타 그림(장면별 7종). */
@DrawableRes
fun defaultAvatarOf(expression: AvatarExpression, character: AvatarCharacter = AvatarCharacter.MALE): Int =
    if (character == AvatarCharacter.FEMALE) {
        when (expression) {
            AvatarExpression.JOY -> R.drawable.avatar_female_joy
            AvatarExpression.SAD -> R.drawable.avatar_female_sad
            AvatarExpression.STAND -> R.drawable.avatar_female_stand
            AvatarExpression.RUN -> R.drawable.avatar_female_run
            AvatarExpression.FACE -> R.drawable.avatar_female_face
            AvatarExpression.SEARCH -> R.drawable.avatar_female_search
            AvatarExpression.JUMP -> R.drawable.avatar_female_jump
        }
    } else {
        when (expression) {
            AvatarExpression.JOY -> R.drawable.avatar_default_joy
            AvatarExpression.SAD -> R.drawable.avatar_default_sad
            AvatarExpression.STAND -> R.drawable.avatar_default_stand
            AvatarExpression.RUN -> R.drawable.avatar_default_run
            AvatarExpression.FACE -> R.drawable.avatar_default_face
            AvatarExpression.SEARCH -> R.drawable.avatar_default_search
            AvatarExpression.JUMP -> R.drawable.avatar_default_jump
        }
    }

/** 달리기 동작 한 주기(4장, 오른쪽을 향함). 측정 화면 내 아바타가 차례로 넘긴다. 기본 캐릭터 성별을 따른다. */
fun runCycleOf(character: AvatarCharacter = AvatarCharacter.MALE): List<Int> =
    if (character == AvatarCharacter.FEMALE) {
        listOf(
            R.drawable.avatar_female_run_1,
            R.drawable.avatar_female_run_2,
            R.drawable.avatar_female_run_3,
            R.drawable.avatar_female_run_4
        )
    } else {
        listOf(
            R.drawable.avatar_default_run_1,
            R.drawable.avatar_default_run_2,
            R.drawable.avatar_default_run_3,
            R.drawable.avatar_default_run_4
        )
    }

/** 기본 아바타 그림의 시험 표지 접두어(뒤에 표정 이름). */
const val DEFAULT_AVATAR_TAG = "default-avatar-"

/**
 * 배경 장면 위 캐릭터의 밤 조명. 다크 벌에서는 기본 배경이 밤 그림이라 캐릭터 색을 조금 어둡고 푸르게 곱해 장면에 맞춘다
 * (사용자 요청 2026-10-30). 라이트 벌은 null(원래 색).
 */
@Composable
fun sceneAvatarFilter(): ColorFilter? = if (LocalRunkkColors.current.isDark) NIGHT_SCENE_FILTER else null

private val NIGHT_SCENE_FILTER = ColorFilter.lighting(multiply = Color(0xFFE6E9F2), add = Color.Black)

package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 아바타 한 칸(D-254). 아바타 꾸미기(AVATAR_CUSTOMIZE)가 닫혀 있으면 [faceKey]를 무시하고 기본 아바타의 [expression] 그림을 그리고,
 * 열려 있으면 기존 얼굴 그림([RunkkAvatarPortrait])을 쓴다. 홈·마이·결과·랭킹 행·매칭 성공 등이 모두 이 하나를 부른다.
 * 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun RunkkAvatar(
    faceKey: String?,
    expression: AvatarExpression,
    modifier: Modifier = Modifier,
    faceLayers: List<FaceLayer> = emptyList()
) {
    if (LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)) {
        RunkkAvatarPortrait(faceKey ?: RunkkAvatarArt.DEFAULT_FACE_KEY, faceLayers, modifier)
    } else {
        Image(
            painterResource(defaultAvatarOf(expression)),
            contentDescription = null,
            contentScale = if (expression == AvatarExpression.FACE) ContentScale.Crop else ContentScale.Fit,
            // 전신 그림은 칸 바닥에 발을 붙인다(가운데 맞춤이면 발밑이 떠 보인다). 얼굴은 가운데
            alignment = if (expression == AvatarExpression.FACE) Alignment.Center else Alignment.BottomCenter,
            modifier = modifier.testTag(DEFAULT_AVATAR_TAG + expression.name)
        )
    }
}

/**
 * 배틀런 상대 그림(피그마 C-3의 기본 남자 아바타). 상대의 아바타를 받지 않으므로 모든 상대가 같은 그림이다. 피그마 결과 화면(C-7)의
 * 3D SD 남자 캐릭터는 잘못 들어간 것이라 쓰지 않는다(사용자 확인 2026-10-30). 장식이다.
 */
@Composable
fun RivalAvatar(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.avatar_rival_stand),
        contentDescription = null,
        modifier = modifier.testTag(RIVAL_AVATAR_TAG)
    )
}

/** 배틀런 상대 그림의 시험 표지. */
const val RIVAL_AVATAR_TAG = "rival-avatar"

/** 기본 아바타 그림(장면별 7종). */
@DrawableRes
fun defaultAvatarOf(expression: AvatarExpression): Int = when (expression) {
    AvatarExpression.JOY -> R.drawable.avatar_default_joy
    AvatarExpression.SAD -> R.drawable.avatar_default_sad
    AvatarExpression.STAND -> R.drawable.avatar_default_stand
    AvatarExpression.RUN -> R.drawable.avatar_default_run
    AvatarExpression.SEARCH -> R.drawable.avatar_default_search
    AvatarExpression.JUMP -> R.drawable.avatar_default_jump
    AvatarExpression.FACE -> R.drawable.avatar_default_face
}

/** 기본 아바타 그림의 시험 표지 접두어(뒤에 표정 이름). */
const val DEFAULT_AVATAR_TAG = "default-avatar-"

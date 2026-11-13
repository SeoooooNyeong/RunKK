package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 아바타 원 자리: 지름 [size]의 [background](기본 surfaceHigh) 원에 [borderColor] 1 테두리(기본 없음). [content]가 있으면 원 안에 그린다
 * (사진·이니셜 등). 장식이라 보조 기술에는 숨긴다(이름은 줄이 읽는다). 채팅 작성자 32, 크루원 36, 크루 팝업 80 등.
 */
@Composable
fun RunkkAvatarCircle(
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = RunkkColors.SurfaceHigh,
    borderColor: Color = Color.Transparent,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        content?.invoke(this)
    }
}

/**
 * 아바타 얼굴을 넣은 원 자리: [RunkkAvatarCircle] 안에 [RunkkAvatar](얼굴, [faceKey]·[faceLayers]·[character])를 가득 그린다.
 * 다른 러너면 그 러너의 표시 성별로 [character]를 넘긴다.
 */
@Composable
fun RunkkAvatarCircle(
    faceKey: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    faceLayers: List<FaceLayer> = emptyList(),
    character: AvatarCharacter = LocalAvatarCharacter.current,
    isFaceLoading: Boolean = false,
    background: Color = RunkkColors.SurfaceHigh,
    borderColor: Color = Color.Transparent
) {
    RunkkAvatarCircle(size = size, modifier = modifier, background = background, borderColor = borderColor) {
        RunkkAvatar(
            faceKey = faceKey,
            expression = AvatarExpression.FACE,
            modifier = Modifier.fillMaxSize(),
            faceLayers = faceLayers,
            character = character,
            isFaceLoading = isFaceLoading
        )
    }
}

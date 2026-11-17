package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** 결과 무대 배경 그림의 시험 표지. */
const val RESULT_STAGE_BACKDROP_TAG = "result-stage-backdrop"

/** 결과 무대와 기록 카드 그림 칸의 기본 서기 그림(전신 정지 그림을 못 그릴 때)의 시험 표지. */
const val RESULT_STAGE_DEFAULT_AVATAR_TAG = "result-stage-default-avatar"

/**
 * 러닝 결과 위쪽 무대([임시:D-성-281], [임시:FR-성-33]): 무대 배경 그림, 발밑 그림자, 전신 아바타 칸을 [ResultStageLayout.forResult]
 * 좌표에 놓는다. 좌표의 원점은 물리 화면의 위쪽 끝이라([임시:D-성-309]) 화면이 상태 표시줄 아래에서 시작하는 만큼([topInset]) 위로 올려
 * 그린다(배경은 상태 표시줄 뒤까지 이어진다). 부르는 쪽은 화면 맨 위(뿌리 Box의 첫 자식)에 두고, 내용 시트는 [resultSheetTop]부터 올린다.
 * 배경은 [backgroundKey]의 무대 그림이고 다크 벌에서는 밤 그림이다. 아바타 칸 내용은 [avatar]가 그린다(넘긴 Modifier가 칸 크기다,
 * 발끝이 칸 바닥). 디자인 시스템은 전신 정지 그림 칸(core.navigation)을 모르므로 부르는 쪽이 채운다. 장식이라 읽기 정보는 없다.
 */
@Composable
fun ResultStage(
    backgroundKey: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = resultStageTopInset(),
    avatar: @Composable (Modifier) -> Unit
) {
    val isDark = LocalRunkkColors.current.isDark
    val painter = painterResource(avatarStageBackdropRes(backgroundKey, isDark = isDark))
    val aspect = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 0f }
    val shadowAlpha = if (isDark) ResultStageLayout.SHADOW_ALPHA_DARK else ResultStageLayout.SHADOW_ALPHA_LIGHT
    Layout(
        content = {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.testTag(RESULT_STAGE_BACKDROP_TAG)
            )
            Canvas(Modifier) {
                val layout = ResultStageLayout.forResult(size.width / density, aspect)
                drawFeetShadow(
                    center = Offset(size.width / 2, layout.feetY * density),
                    width = layout.shadowWidth * density,
                    height = layout.shadowHeight * density,
                    alpha = shadowAlpha,
                    blur = ResultStageLayout.SHADOW_BLUR_DP * density
                )
            }
            avatar(Modifier)
        },
        // 무대 전체를 상태 표시줄 높이만큼 올린 뒤 부르는 쪽 Modifier를 건다: 투명도 층(쪽 넘김)이 상태 표시줄 뒤 부분까지 담는다.
        modifier = Modifier.offset { IntOffset(0, -topInset.roundToPx()) }.then(modifier).fillMaxWidth()
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val layout = ResultStageLayout.forResult(width / density, aspect)
        fun Rect.px() = Rect(left * density, top * density, right * density, bottom * density)
        val backdrop = layout.background.px()
        val figure = layout.avatar.px()
        val height = max(backdrop.bottom, ResultStageLayout.SHEET_TOP * density).roundToInt().coerceAtLeast(0)
        val backdropPlaceable = measurables[0].measure(fixed(backdrop))
        val shadowPlaceable = measurables[1].measure(Constraints.fixed(width, height))
        val avatarPlaceables = measurables.drop(2).map { it.measure(fixed(figure)) }
        layout(width, height) {
            backdropPlaceable.place(backdrop.left.roundToInt(), backdrop.top.roundToInt())
            shadowPlaceable.place(0, 0)
            avatarPlaceables.forEach { it.place(figure.left.roundToInt(), figure.top.roundToInt()) }
        }
    }
}

/**
 * 기록 카드 그림 칸의 무대([임시:D-성-290], [임시:FR-성-34]): 칸을 무대 배경 그림으로 채워 아래에 맞추고, 보이는 트랙 가운데에 전신
 * 아바타 칸(칸 높이의 0.80, 발끝이 칸 바닥)과 발밑 그림자를 [ResultStageLayout.forCard] 좌표로 놓는다. 칸 크기와 모서리는 부르는 쪽
 * [modifier]가 정한다(넘친 그림은 칸 경계에서 자른다). 배경은 [backgroundKey]의 무대 그림이고 다크 벌에서는 밤 그림이다.
 */
@Composable
fun ResultCardStage(backgroundKey: String?, modifier: Modifier = Modifier, avatar: @Composable (Modifier) -> Unit) {
    val isDark = LocalRunkkColors.current.isDark
    val painter = painterResource(avatarStageBackdropRes(backgroundKey, isDark = isDark))
    val aspect = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 0f }
    val shadowAlpha = if (isDark) ResultStageLayout.SHADOW_ALPHA_DARK else ResultStageLayout.SHADOW_ALPHA_LIGHT
    Layout(
        content = {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.testTag(RESULT_STAGE_BACKDROP_TAG)
            )
            Canvas(Modifier) {
                val layout = ResultStageLayout.forCard(size.width, size.height, aspect)
                drawFeetShadow(
                    center = Offset(size.width / 2, layout.feetY),
                    width = layout.shadowWidth,
                    height = layout.shadowHeight,
                    alpha = shadowAlpha,
                    // 흐림 4dp는 아바타 높이 180 기준이다. 칸이 작으면 같은 비율로 줄인다.
                    blur = ResultStageLayout.SHADOW_BLUR_DP * layout.avatar.height / ResultStageLayout.AVATAR_H
                )
            }
            avatar(Modifier)
        },
        modifier = modifier.clipToBounds()
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val layout = ResultStageLayout.forCard(width.toFloat(), height.toFloat(), aspect)
        val backdropPlaceable = measurables[0].measure(fixed(layout.background))
        val shadowPlaceable = measurables[1].measure(Constraints.fixed(width, height))
        val avatarPlaceables = measurables.drop(2).map { it.measure(fixed(layout.avatar)) }
        layout(width, height) {
            backdropPlaceable.place(layout.background.left.roundToInt(), layout.background.top.roundToInt())
            shadowPlaceable.place(0, 0)
            avatarPlaceables.forEach { it.place(layout.avatar.left.roundToInt(), layout.avatar.top.roundToInt()) }
        }
    }
}

/** 결과 무대가 위로 올려 그릴 높이: 상태 표시줄 높이(화면은 그 아래에서 시작한다, [임시:D-성-309]). */
@Composable
fun resultStageTopInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** 결과 내용 시트가 시작하는 높이(화면 안 좌표): 물리 화면 위에서 300, 화면은 상태 표시줄 아래에서 시작한다. */
@Composable
fun resultSheetTop(): Dp = (ResultStageLayout.SHEET_TOP.dp - resultStageTopInset()).coerceAtLeast(0.dp)

/**
 * 결과 무대와 기록 카드의 기본 서기 그림([임시:D-성-282]): 전신 정지 그림 칸이 비었거나 아바타 꾸미기가 닫혀 있을 때 세우는 성별 기본
 * 전신 그림이다. 발을 칸 바닥에 붙이고, 다크 벌 밤 장면에서는 조금 어둡게 그린다.
 */
@Composable
fun ResultStageDefaultAvatar(modifier: Modifier = Modifier) {
    Image(
        painterResource(defaultAvatarOf(AvatarExpression.STAND, LocalAvatarCharacter.current)),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        colorFilter = sceneAvatarFilter(),
        modifier = modifier.testTag(RESULT_STAGE_DEFAULT_AVATAR_TAG)
    )
}

/**
 * 발밑 그림자([임시:D-성-287]): 가운데가 진하고 가장자리 [blur]만큼 흐려지는 타원. 홈 무대 그림자(core.avatar)와 같은 모양이다
 * (Modifier.blur는 API 31부터라 방사 그라데이션으로 그린다).
 */
internal fun DrawScope.drawFeetShadow(center: Offset, width: Float, height: Float, alpha: Float, blur: Float) {
    val radiusX = width / 2 + blur
    val radiusY = height / 2 + blur
    if (radiusX <= 0f || radiusY <= 0f) return
    val solid = ((width / 2 - blur) / radiusX).coerceIn(0f, 1f)
    val color = Color.Black.copy(alpha = alpha)
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                solid to color,
                1f to Color.Transparent,
                center = center,
                radius = radiusX
            ),
            radius = radiusX,
            center = center
        )
    }
}

private fun fixed(rect: Rect): Constraints =
    Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0))

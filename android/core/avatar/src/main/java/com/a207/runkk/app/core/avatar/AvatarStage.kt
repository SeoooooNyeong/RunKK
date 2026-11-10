package com.a207.runkk.app.core.avatar

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import timber.log.Timber

/** 로딩 표시의 시험 태그([AvatarLoadingPlaceholder]). 화면 시험이 표시 유무를 찾을 때 쓴다. */
const val AVATAR_LOADING_TAG = "avatar_loading"

/**
 * 아바타 무대의 틀(design-oo 05-30 4절, [임시:D-성-107], [임시:D-성-108]): 배경 그림 → 바닥 그림자 → 아트보드 상자 → [overlay] 순으로
 * 겹친다. 칸 크기는 부르는 쪽이 고정한다(이 틀은 받은 최대 크기를 칸으로 본다).
 *
 * - [backdrop]은 아래 가운데 정렬로 잘라 채운다(`ContentScale.Crop`, `Alignment.BottomCenter`). 바닥선 = 칸 아래 − 0.18 × 그려진
 *   그림 높이. 그림 크기는 [backdropSize](기본: 그림의 고유 크기, 모르면 칸 비율)다.
 * - [backdropSize]가 null이면 배경 없는 칸(러닝 중 지도 마커)이다: 바닥선 94%, 아트보드 1.17 × 칸 높이(칸 아래로 넘친 발 아래 빈 자리는
 *   잘린다).
 * - 그림자는 앱이 그린다: 타원 0.24A × 0.048A, 중심 = 칸 가운데 바닥선, 검정 라이트 18% / 다크 24%([darkTheme]), 흐림 4dp
 *   ([임시:D-성-132]). riv 안의 그림자는 숨겨 두었다.
 * - [artboard]는 아트보드 상자(너비 0.8A, 높이 A, 발끝 줄이 바닥선) 안에 그린다. Rive 뷰나 앞 단계 그림을 `fillMaxSize`로 넣는다.
 *   계산 결과([AvatarStageLayout], px)를 함께 받는다.
 */
@Composable
fun AvatarStageFrame(
    modifier: Modifier = Modifier,
    backdrop: Painter? = null,
    darkTheme: Boolean = false,
    backdropSize: Size? = backdrop?.intrinsicSize,
    showShadow: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    artboard: @Composable BoxScope.(AvatarStageLayout) -> Unit
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val layout = remember(width, height, backdropSize) {
            if (backdropSize == null) {
                AvatarStageLayout.withoutBackdrop(width, height)
            } else if (backdropSize.hasArea()) {
                AvatarStageLayout.of(width, height, backdropSize.width, backdropSize.height)
            } else {
                AvatarStageLayout.of(width, height, 0f, 0f)
            }
        }
        if (backdrop != null) {
            Image(
                painter = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                alignment = Alignment.BottomCenter,
                contentScale = ContentScale.Crop
            )
        }
        if (showShadow) {
            val alpha = RiveAvatarGeometry.shadowAlpha(darkTheme)
            Canvas(Modifier.matchParentSize()) {
                drawStageShadow(layout, alpha, RiveAvatarGeometry.SHADOW_BLUR_DP.dp.toPx())
            }
        }
        Box(Modifier.artboardBox(layout)) { artboard(layout) }
        overlay()
    }
}

/**
 * 내 Rive 아바타 무대(홈·내 러너·꾸미기·해금 팝업·러닝 중 칸이 함께 쓰는 「배경 + 바닥선 + 그림자 + Rive + 로딩」 상자,
 * design-oo 05-30 2절·3절·4절). [AvatarStageFrame] 위에 [RunkkRiveAvatar]와 [AvatarLoadingPlaceholder]를 얹는다.
 *
 * - [look]이 null이면(아직 준비 중) 배경과 그림자만 깔고 로딩 표시를 보인다(빈 칸 0프레임, [임시:NFR-성-4]).
 * - 로딩 표시([showLoading]): 첫 그림이 100ms 안에 보이면 그리지 않고, 아니면 가운데 원형 표시(32dp, 선 3dp, [loadingColor])를 보이다가
 *   그림이 보이면 200ms 페이드로 거둔다([임시:D-성-106]). 러닝 중 칸은 false.
 * - [mirrored]: 달리기([motion] = RUN)일 때만 좌우로 뒤집는다. 뒤집기 전후 몸 위치 차이가 칸 너비 3%를 넘으면 그만큼 옮긴다
 *   ([AvatarStageLayout.mirrorShift], [임시:D-성-133]). 그림자·기준점(칸 아래 가운데)은 그대로다.
 * - [onError]: Rive를 그리지 못했다(네이티브 없음, 자산 실패). 로딩 표시를 거둔다. 부르는 쪽이 성별 기본 그림으로 바꾼다
 *   ([AvatarStageFrame]에 앞 단계 그림을 넣으면 같은 바닥선에 선다).
 * - 디버그 로그 `AvatarStage shown=<ms> ready=<ms>`(SystemClock.uptimeMillis)로 표시 시간을 잰다(앱은 디버그 빌드에서만 기록).
 */
@Composable
fun RiveAvatarStage(
    look: RiveAvatarLook?,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    motion: AvatarMotion = AvatarMotion.STAND,
    backdrop: Painter? = null,
    mirrored: Boolean = false,
    showLoading: Boolean = true,
    loadingColor: Color = Color.Gray,
    onReady: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
) {
    val shownAt = remember { SystemClock.uptimeMillis() }
    var isReady by remember(look?.gender, look?.headLayers, motion) { mutableStateOf(false) }
    var isFailed by remember(look?.gender, look?.headLayers) { mutableStateOf(false) }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    AvatarStageFrame(
        modifier = modifier,
        backdrop = backdrop,
        darkTheme = darkTheme,
        backdropSize = backdrop?.intrinsicSize,
        overlay = {
            if (showLoading && !isFailed) {
                AvatarLoadingPlaceholder(
                    isReady = look != null && isReady,
                    color = loadingColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    ) { layout ->
        if (look != null) {
            val flip = mirrored && motion == AvatarMotion.RUN
            val shift = if (flip) layout.mirrorShift().roundToInt() else 0
            RunkkRiveAvatar(
                gender = look.gender,
                motion = motion,
                outfit = look.outfit,
                bodyShape = look.bodyShape,
                runningAmount = look.runningAmount,
                headLayers = look.headLayers,
                modifier = Modifier.fillMaxSize().offset { IntOffset(shift, 0) },
                onError = {
                    isFailed = true
                    currentOnError(it)
                },
                mirrored = flip,
                onReady = {
                    isReady = true
                    isFailed = false // 앞서 실패했어도 그림이 보이면 로딩 표시 규칙으로 돌아온다
                    Timber.d("AvatarStage shown=%d ready=%d", shownAt, SystemClock.uptimeMillis())
                    currentOnReady()
                }
            )
        }
    }
}

/**
 * 아바타 로딩 표시([임시:D-성-106]). [isReady]가 false로 100ms 넘게 이어지면 원형 진행 표시(지름 32dp, 선 3dp, [color])를 보이고,
 * true가 되면 200ms 페이드로 거둔다. 100ms 안에 true가 되면 아예 그리지 않는다(깜박임 방지). 칸 가운데에 놓는다.
 */
@Composable
fun AvatarLoadingPlaceholder(isReady: Boolean, color: Color, modifier: Modifier = Modifier) {
    AvatarLoadingPlaceholder(isReady, color, modifier, remember { Animatable(1f) })
}

/** [alpha]를 밖에서 받는 형태(시험이 페이드 값을 본다). */
@Composable
internal fun AvatarLoadingPlaceholder(
    isReady: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Animatable<Float, AnimationVector1D>
) {
    var isShown by remember { mutableStateOf(false) }
    LaunchedEffect(isReady) {
        when {
            isReady && isShown -> try {
                alpha.animateTo(0f, tween(LOADING_FADE_MILLIS))
            } finally {
                // 페이드가 끝났을 때만 거둔다. 중간에 취소되면(다시 준비 전) 다음 효과가 이어받는다.
                if (alpha.value == 0f) isShown = false
            }

            isReady -> Unit

            // 이미 보이던 표시(페이드가 취소됐다)는 기다리지 않고 바로 불투명하게 되돌린다.
            isShown -> alpha.snapTo(1f)

            else -> {
                delay(LOADING_SHOW_DELAY_MILLIS)
                alpha.snapTo(1f)
                isShown = true
            }
        }
    }
    if (isShown) {
        CircularProgressIndicator(
            modifier = modifier
                .testTag(AVATAR_LOADING_TAG)
                .size(LOADING_SIZE_DP.dp)
                .graphicsLayer { this.alpha = alpha.value },
            color = color,
            strokeWidth = LOADING_STROKE_DP.dp
        )
    }
}

/** 로딩 표시 수치([임시:D-성-106]): 100ms 뒤에 보이고, 200ms 페이드, 지름 32dp, 선 3dp. */
private const val LOADING_SHOW_DELAY_MILLIS = 100L
private const val LOADING_FADE_MILLIS = 200
private const val LOADING_SIZE_DP = 32
private const val LOADING_STROKE_DP = 3

private fun Size.hasArea(): Boolean = this != Size.Unspecified && width > 0f && height > 0f

/** 아트보드 상자: 계산한 크기로 재고 계산한 자리에 놓는다(칸보다 커도 줄이지 않는다). 상자 자신은 칸 크기를 차지한다. */
private fun Modifier.artboardBox(layout: AvatarStageLayout): Modifier = layout { measurable, constraints ->
    val width = layout.artboardWidth.roundToInt().coerceAtLeast(0)
    val height = layout.artboardHeight.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(layout.artboardLeft.roundToInt(), layout.artboardTop.roundToInt())
    }
}

/** 바닥 그림자: 가운데가 진하고 가장자리 [blur]만큼 흐려지는 타원(Modifier.blur는 API 31부터라 방사 그라데이션으로 그린다). */
private fun DrawScope.drawStageShadow(layout: AvatarStageLayout, alpha: Float, blur: Float) {
    val radiusX = layout.shadowWidth / 2 + blur
    val radiusY = layout.shadowHeight / 2 + blur
    if (radiusX <= 0f || radiusY <= 0f) return
    val center = Offset(layout.shadowCenterX, layout.shadowCenterY)
    val solid = ((layout.shadowWidth / 2 - blur) / radiusX).coerceIn(0f, 1f)
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

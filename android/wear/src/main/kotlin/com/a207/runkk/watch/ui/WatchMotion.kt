package com.a207.runkk.watch.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/*
 * 워치 화면 모션(W41, 피그마 "Runkk · Galaxy Watch (화면별 + 모션)" 프로토타입). 피그마가 정한 곳은 그 값을, 정하지 않은 곳은 메인
 * 캐러셀과 같은 말(Smart Animate 320ms, [CarouselEasing])을 쓴다.
 * - 화면 사이: 피그마 DISSOLVE(새 화면이 옛 화면 위로 나타남) EASE_OUT 220ms, 매칭 중·매칭 완료가 끼면 250ms.
 * - 거리 다이얼 −·+: 피그마 컴포넌트 변형 SMART_ANIMATE 250ms, 넘쳤다 돌아오는 곡선 [DialEasing].
 * - 그 밖의 값 변화·등장(카운트다운 숫자, 측정 거리·고리·띠, 결과·매칭 완료 등장, 일시정지 버튼): 320ms [CarouselEasing].
 * 저전력 표시·애니메이션 끔·절전 모드면 [LocalMotionStill]이 참이고 모두 바로 바뀐다.
 */

/** 움직이지 않고 바로 바꿀 때 참(저전력 표시, 시스템 애니메이션 끔, 절전 모드). [WatchRoot]가 정한다. */
internal val LocalMotionStill = staticCompositionLocalOf { false }

/** 피그마 EASE_OUT(cubic-bezier(0, 0, 0.58, 1)): 화면 사이 DISSOLVE와 사라짐. */
internal val ScreenEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** 화면 사이 DISSOLVE 길이(피그마 F1→FC3, FC3→측정, 측정→결과, 결과→메인). */
internal const val SCREEN_FADE_MS = 220

/** 매칭 중·매칭 완료가 낀 DISSOLVE 길이(피그마 B1→BMA, BMA→BMB, BMB→BC1). */
internal const val MATCH_FADE_MS = 250

/** 거리 다이얼 −·+ 곡선(피그마 변형 km=… SMART_ANIMATE, cubic-bezier(0.3, 1.4, 0.6, 1))과 길이. */
internal val DialEasing = CubicBezierEasing(0.3f, 1.4f, 0.6f, 1f)
internal const val DIAL_MS = 250

/** 결과 값 칸이 차례로 나타나는 간격. */
internal const val REVEAL_STAGGER_MS = 60

/** 카운트다운 한 숫자 동안 둘레 원이 다 차는 시간(1초 박자). */
internal const val COUNTDOWN_FILL_MS = 1_000

/** [still]이면 바로, 아니면 [durationMs]·[easing]으로 움직이는 사양. */
internal fun <T> motionSpec(still: Boolean, durationMs: Int, easing: Easing, delayMs: Int = 0): FiniteAnimationSpec<T> =
    if (still) snap() else tween(durationMs, delayMs, easing)

/** 지금 화면의 모션 사양([LocalMotionStill]을 읽는다). */
@Composable
@ReadOnlyComposable
internal fun <T> motionSpec(durationMs: Int, easing: Easing, delayMs: Int = 0): FiniteAnimationSpec<T> =
    motionSpec(LocalMotionStill.current, durationMs, easing, delayMs)

/**
 * 화면에 들어올 때 한 번 0에서 1로 가는 등장 진행(320ms, [order]번째면 [REVEAL_STAGGER_MS]씩 늦게). 움직임 줄이기면 처음부터 1이다.
 * 값은 [revealLayer]가 그리기 단계에서 읽는다.
 */
@Composable
internal fun rememberReveal(order: Int = 0): Animatable<Float, AnimationVector1D> {
    val still = LocalMotionStill.current
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) {
        if (still) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(CAROUSEL_DURATION_MS, order * REVEAL_STAGGER_MS, CarouselEasing))
        }
    }
    return progress
}

/**
 * 등장 진행([progress])에 따라 흐림에서 또렷하게, [rise]만큼 아래에서(음수면 위에서), [shift]만큼 옆에서, [fromScale] 크기에서 제자리로
 * 온다. 그리기 층만 바꾸므로 다시 구성하거나 배치하지 않는다.
 */
internal fun Modifier.revealLayer(
    progress: Animatable<Float, AnimationVector1D>,
    rise: Dp = 0.dp,
    shift: Dp = 0.dp,
    fromScale: Float = 1f
): Modifier = graphicsLayer {
    val p = progress.value
    val left = 1f - p
    alpha = p.coerceIn(0f, 1f)
    translationY = rise.toPx() * left
    translationX = shift.toPx() * left
    val scale = fromScale + (1f - fromScale) * p
    scaleX = scale
    scaleY = scale
}

/**
 * 화면 전환 단위. 상태 종류가 같으면 같은 화면이고 값만 바뀐다(전환 없음). 동행 표시는 측정·일시정지 두 화면으로 나눈다.
 */
internal fun screenKey(flow: WatchFlow, companion: WatchUiState): Any = when (flow) {
    WatchFlow.Companion ->
        if (companion.toMeasureFlow() is WatchFlow.Paused) COMPANION_PAUSED_KEY else COMPANION_RUN_KEY

    else -> flow::class
}

/** [from] 화면에서 [to] 화면으로 갈 때 DISSOLVE 길이. 매칭 중·매칭 완료가 끼면 250ms, 아니면 220ms다(피그마). */
internal fun screenFadeMillis(from: Any, to: Any): Int =
    if (from in MATCH_KEYS || to in MATCH_KEYS) MATCH_FADE_MS else SCREEN_FADE_MS

/**
 * 측정 고리가 [from]에서 [to]로 갈 때 구간을 넘겼는가(무제한 1km 구간 고리가 거의 찼다가 0 근처로 돌아옴). 넘겼으면 끝까지 채운 뒤
 * 0에서 다시 채운다(거꾸로 감기지 않게).
 */
internal fun isLapWrap(from: Float, to: Float): Boolean = to < from - LAP_WRAP_GAP

/**
 * 시스템 애니메이션이 꺼졌거나(접근성 애니메이션 삭제, 개발자 옵션 배율 0) 절전 모드면 움직임을 줄인다. 화면이 떠 있는 동안 바뀌어도
 * 따라간다(WR16-1, 측정 화면은 러닝 내내 떠 있다): 절전 모드는 ACTION_POWER_SAVE_MODE_CHANGED로, 애니메이터 배율은 API 33부터
 * 배율 변경 리스너로 다시 판단한다.
 */
@Composable
internal fun rememberMotionReduced(): Boolean {
    val context = LocalContext.current
    return produceState(initialValue = isMotionReduced(context), context) {
        val update = { value = isMotionReduced(context) }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = update()
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val scaleListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ValueAnimator.DurationScaleChangeListener { update() }
                    .also(ValueAnimator::registerDurationScaleChangeListener)
            } else {
                null
            }
        update()
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (scaleListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ValueAnimator.unregisterDurationScaleChangeListener(scaleListener)
            }
        }
    }.value
}

private fun isMotionReduced(context: Context): Boolean =
    areAnimatorsOff() || context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

/** 애니메이터 배율이 0인가(접근성 애니메이션 삭제, 개발자 옵션). API 33부터는 배율을 바로 읽는다. */
private fun areAnimatorsOff(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ValueAnimator.getDurationScale() == 0f
} else {
    !ValueAnimator.areAnimatorsEnabled()
}

private const val COMPANION_RUN_KEY = "companion-run"
private const val COMPANION_PAUSED_KEY = "companion-paused"
private val MATCH_KEYS: Set<Any> = setOf(WatchFlow.Matching::class, WatchFlow.Matched::class)

/** 고리가 이만큼 넘게 줄면 구간을 넘긴 것으로 본다. */
private const val LAP_WRAP_GAP = 0.5f

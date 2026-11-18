package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 측정 화면 아바타(자유런·대결 공통, 사용자 요청 2026-10-30): 움직이면 달리기 동작 4장(runCycleOf)을 [frameMillis]마다 넘기고,
 * 멈추면 서 있는 그림이다. 움직임 줄이기면 달리기 첫 장에 멈춘다. [character]는 기본이 내 캐릭터이고 상대는 부르는 쪽이 정한다.
 * 기본 캐릭터가 아닌 그림(셀럽 전용 캐릭터 [CelebrityCharacter])은 [stand]·[runCycle]로 넘긴다.
 */
@Composable
fun RunkkRunningFigure(
    isMoving: Boolean,
    frameMillis: Long,
    modifier: Modifier = Modifier,
    character: AvatarCharacter = LocalAvatarCharacter.current,
    alpha: Float = 1f,
    isFacingLeft: Boolean = false,
    @DrawableRes stand: Int = defaultAvatarOf(AvatarExpression.STAND, character),
    runCycle: List<Int> = runCycleOf(character)
) {
    if (!isMoving) {
        Image(
            painterResource(stand),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            modifier = modifier.fillMaxSize().alpha(alpha)
        )
        return
    }
    val frames = runCycle
    val reduceMotion = LocalReduceMotion.current
    var frame by remember { mutableIntStateOf(0) }
    val interval by rememberUpdatedState(frameMillis)
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(interval)
            frame = (frame + 1) % frames.size
        }
    }
    Image(
        painterResource(frames[frame % frames.size]),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        // 달리기 그림은 오른쪽을 보므로 왼쪽으로 달릴 때는 좌우로 뒤집는다
        modifier = modifier.fillMaxSize().alpha(alpha).graphicsLayer { scaleX = if (isFacingLeft) -1f else 1f }
    )
}

/**
 * 진행 방향이 왼쪽(서쪽)인가. 지도·격자 모두 북쪽이 위라 경도가 화면 가로다. 경로 끝 [RouteLine.DIRECTION_METERS] 전 지점에서
 * 끝점으로 경도가 줄면 왼쪽이다. 남북으로만 움직여 애매하거나 경로가 짧으면 이전 방향을 지킨다(처음은 오른쪽).
 */
@Composable
fun rememberHeadingLeft(route: RouteLine): Boolean {
    var isLeft by remember { mutableStateOf(false) }
    val now = headingLeftOf(route)
    LaunchedEffect(now) { if (now != null) isLeft = now }
    return now ?: isLeft
}

private fun headingLeftOf(route: RouteLine): Boolean? {
    if (!route.isLongEnoughForDirection) return null
    val from = route.pointAtMeters(route.lengthMeters - RouteLine.DIRECTION_METERS) ?: return null
    val dLon = route.points.last().longitude - from.longitude
    return if (abs(dLon) < MIN_EAST_WEST_DEGREES) null else dLon < 0
}

/** 동서 방향을 정할 최소 경도 차(약 3m). 이보다 작으면 남북으로 달리는 중이다. */
private const val MIN_EAST_WEST_DEGREES = 0.00003

/** 거리가 늘면 움직이는 중, [STILL_AFTER_MILLIS] 동안 그대로면 멈춘 것으로 본다(현재 페이스는 마지막 값을 들고 있어 쓰지 않는다). */
@Composable
fun rememberIsMoving(distance: Distance?): Boolean {
    var isMoving by remember { mutableStateOf(false) }
    LaunchedEffect(distance) {
        if (distance == null || distance.meters <= 0) return@LaunchedEffect
        isMoving = true
        delay(STILL_AFTER_MILLIS)
        isMoving = false
    }
    return isMoving
}

/**
 * 측정 중 내 아바타가 달리기 자세인지(S15P31A207-335 2차). [isActive](측정 중이고 멈춤 아님)가 켜지면 거리·페이스로 움직임을 판단하기를
 * 기다리지 않고 첫 프레임부터 달린다. 그 뒤 거리([distance])가 [STILL_AFTER_MILLIS] 동안 그대로면 선다(D-856 멈춤 규칙). 측정 시작
 * 직후에는 GPS 첫 값과 현재 페이스(30초 창, 값 두 개 필요)가 몇 초 늦어 정면 서기로 출발하던 것을 고친다.
 */
@Composable
fun rememberRunPose(distance: Distance?, isActive: Boolean): Boolean {
    var isRunning by remember { mutableStateOf(isActive) }
    LaunchedEffect(distance, isActive) {
        isRunning = isActive
        if (!isActive) return@LaunchedEffect
        delay(STILL_AFTER_MILLIS)
        isRunning = false
    }
    return isActive && isRunning
}

/** 달리기 동작 한 장의 시간: 6:00/km에서 [RUN_FRAME_MILLIS_AT_REF], 빠를수록 짧게. 페이스를 모르면 기준값. */
fun runFrameMillis(pace: Pace?): Long {
    val seconds = pace?.secondsPerKm ?: return RUN_FRAME_MILLIS_AT_REF
    return (RUN_FRAME_MILLIS_AT_REF * seconds / REF_PACE_SECONDS).coerceIn(RUN_FRAME_MILLIS_MIN, RUN_FRAME_MILLIS_MAX)
}

/** Rive 달리기 동작 재생 빠르기: 6:00/km에서 1, 빠를수록 크게([runFrameMillis]와 같은 비율, 약 0.54~2.14). */
fun runMotionSpeed(pace: Pace?): Float = RUN_FRAME_MILLIS_AT_REF.toFloat() / runFrameMillis(pace)

private const val STILL_AFTER_MILLIS = 3_000L
private const val REF_PACE_SECONDS = 360L
private const val RUN_FRAME_MILLIS_AT_REF = 150L
private const val RUN_FRAME_MILLIS_MIN = 70L
private const val RUN_FRAME_MILLIS_MAX = 280L

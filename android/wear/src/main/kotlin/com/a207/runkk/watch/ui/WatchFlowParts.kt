package com.a207.runkk.watch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender

/** 트랙 그림 배경(피그마 Q1). [isDimmed]면 글자가 잘 보이게 푸른 막을 깐다(피그마 F1·FR3·결과 화면). */
@Composable
internal fun TrackBackground(
    isDimmed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(WatchColors.Background)) {
        Image(
            painter = painterResource(R.drawable.watch_track),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isDimmed) Box(Modifier.fillMaxSize().background(WatchColors.Scrim))
        content()
    }
}

/**
 * 화면 둘레 고리(피그마 F1·FR3). [fraction]만큼 [color]로 채운다(왼쪽 아래에서 시계 방향으로 오른쪽 아래까지 270도 호, 아래 90도는 비운다).
 * [track]은 남은 부분 색이고 null이면 그리지 않는다. [hasStartKnob]이면 시작점에 둥근 손잡이를 얹는다(피그마 FR3·BR1·SR3).
 */
@Composable
internal fun EdgeRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color? = WatchColors.Dark,
    width: Dp = 5.dp,
    hasStartKnob: Boolean = false
) {
    EdgeRing({ fraction }, { color }, modifier, track, width, hasStartKnob)
}

/**
 * 움직이는 고리. 채움([fraction])과 색([color])을 그리기 단계에서 읽어, 값이 움직이는 동안 화면을 다시 구성하지 않고 고리만 다시
 * 그린다(측정 1초 갱신·배터리).
 */
@Composable
internal fun EdgeRing(
    fraction: () -> Float,
    color: () -> Color,
    modifier: Modifier = Modifier,
    track: Color? = WatchColors.Dark,
    width: Dp = 5.dp,
    hasStartKnob: Boolean = false
) {
    // 손잡이가 화면 둘레에 잘리지 않게 그만큼 고리를 안으로 들인다.
    val inset = if (hasStartKnob) RING_PADDING + KNOB_RADIUS - width / 2 else RING_PADDING
    Canvas(modifier = modifier.fillMaxSize().padding(inset)) {
        val stroke = Stroke(width = width.toPx(), cap = StrokeCap.Round)
        val half = stroke.width / 2
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(half, half)
        val fill = color()
        if (track != null) drawArc(track, RING_START, RING_SWEEP, false, topLeft, arcSize, style = stroke)
        val sweep = RING_SWEEP * fraction().coerceIn(0f, 1f)
        if (sweep > 0f) drawArc(fill, RING_START, sweep, false, topLeft, arcSize, style = stroke)
        if (hasStartKnob) {
            val angle = Math.toRadians(RING_START.toDouble())
            val radius = arcSize.width / 2
            val knob = Offset(
                center.x + radius * kotlin.math.cos(angle).toFloat(),
                center.y + radius * kotlin.math.sin(angle).toFloat()
            )
            drawCircle(fill, KNOB_RADIUS.toPx(), knob)
            drawCircle(WatchColors.Ink, KNOB_RADIUS.toPx(), knob, style = Stroke(KNOB_BORDER.toPx()))
        }
    }
}

/** 고리가 시작하는 각도(왼쪽 아래, 7시 반)와 그리는 각도(피그마 F1-10 끝까지 찬 고리: 7시 반 → 4시 반). */
private const val RING_START = 135f
private const val RING_SWEEP = 270f

/** 고리와 화면 가장자리 사이, 시작점 손잡이 반지름과 테두리(피그마 FR3 지름 20px ×0.64). */
private val RING_PADDING = 3.dp
private val KNOB_RADIUS = 6.5.dp
private val KNOB_BORDER = 1.5.dp

/** 거리 이름: 1·3·5·10km는 "3km", 하프·풀은 "하프"·"풀". */
@Composable
internal fun distanceLabel(distance: Distance): String = when (distance.meters) {
    WatchFlowViewModel.HALF_MARATHON_METERS -> stringResource(R.string.distance_half)
    WatchFlowViewModel.FULL_MARATHON_METERS -> stringResource(R.string.distance_full)
    else -> stringResource(R.string.distance_km, kmNumber(distance))
}

/** km 숫자(정수면 소수점 없이). 3,000m → "3". */
internal fun kmNumber(distance: Distance): String {
    val meters = distance.meters
    return if (meters % METERS_PER_KM == 0L) (meters / METERS_PER_KM).toString() else WatchFormat.distanceKm(meters)
}

private const val METERS_PER_KM = 1_000L

/** 모드 이름. */
@Composable
internal fun modeName(mode: WatchMode): String = stringResource(
    when (mode) {
        WatchMode.FREE -> R.string.mode_free
        WatchMode.BATTLE -> R.string.mode_battle
        WatchMode.SELF -> R.string.mode_self
        WatchMode.CELEB -> R.string.mode_celeb
    }
)

/** 서버 티어 코드 → 이름(폰 core.designsystem TierName과 같은 표). 모르는 코드는 그대로 보인다. */
@Composable
internal fun tierName(tier: String): String = when (tier) {
    "UNRANKED" -> stringResource(R.string.tier_unranked)
    "BRONZE" -> stringResource(R.string.tier_bronze)
    "SILVER" -> stringResource(R.string.tier_silver)
    "GOLD" -> stringResource(R.string.tier_gold)
    "PLATINUM" -> stringResource(R.string.tier_platinum)
    "MASTER" -> stringResource(R.string.tier_master)
    else -> tier
}

/** 티어 칩(피그마 BMA "실버"). */
@Composable
internal fun TierChip(tier: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text = tierName(tier),
        modifier = modifier.border(1.dp, WatchColors.OnSurface, shape).padding(horizontal = 8.dp, vertical = 1.dp),
        style = labelStyle(color = WatchColors.OnSurface, size = 11.sp)
    )
}

/** 내 기본 캐릭터 전신 그림(FR-162: 여자면 여자 그림, 그 밖은 남자 그림). 장식이다. */
@Composable
internal fun CharacterImage(gender: Gender?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(
            if (gender == Gender.FEMALE) R.drawable.watch_character_female else R.drawable.watch_character_male
        ),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

/** 캐릭터 얼굴(매칭 완료). 장식이다(이름을 아래에 글자로 둔다). */
@Composable
internal fun FaceImage(gender: Gender?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(
            if (gender ==
                Gender.FEMALE
            ) {
                R.drawable.watch_face_female
            } else {
                R.drawable.watch_face_male
            }
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** 워치 위치 꺼짐 안내를 누르면 부르는 조작(W52). [WatchRoot]가 [WatchActions.onOpenLocationSettings]로 채운다. */
internal val LocalOpenLocationSettings = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 안내 문구 한 줄(트랙 바탕 위 흰 굵은 글자, 나타나면 읽어 준다). 없으면 그리지 않는다. 랜덤 대결 잠금은 [runsLeft]가 1 이상이면
 * "러닝을 n번 더 뛰면 배틀런이 가능해요!", 아니면 남은 수 없는 문구다(폰 BattleModeScreen과 같다). 워치 위치 꺼짐(W52)은 누르면
 * 위치 설정을 연다([LocalOpenLocationSettings]).
 */
@Composable
internal fun FlowNoticeText(
    notice: WatchFlow.Notice?,
    modifier: Modifier = Modifier,
    color: Color = WatchColors.OnSurface,
    runsLeft: Int? = null
) {
    if (notice == null) return
    val count = runsLeft?.takeIf { notice == WatchFlow.Notice.RANDOM_LOCKED && it > 0 }
    val clickable = if (notice == WatchFlow.Notice.LOCATION_OFF) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.open_location_settings),
            onClick = LocalOpenLocationSettings.current
        )
    } else {
        Modifier
    }
    Text(
        text = if (count !=
            null
        ) {
            stringResource(R.string.notice_random_locked, count)
        } else {
            stringResource(noticeRes(notice))
        },
        modifier = modifier.then(clickable).semantics { liveRegion = LiveRegionMode.Polite },
        style = labelStyle(color = color, size = 11.sp).copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center
    )
}

/** 안내 → 문자열 리소스. */
internal fun noticeRes(notice: WatchFlow.Notice): Int = when (notice) {
    WatchFlow.Notice.LOCATION_CONSENT -> R.string.notice_location_consent
    WatchFlow.Notice.LOCATION_PERMISSION -> R.string.notice_location_permission
    WatchFlow.Notice.LOCATION_OFF -> R.string.notice_location_off
    WatchFlow.Notice.PHONE_RUNNING -> R.string.notice_phone_running
    WatchFlow.Notice.BATTLE_OFFLINE -> R.string.notice_battle_offline
    WatchFlow.Notice.MATCH_FAILED -> R.string.notice_match_failed
    WatchFlow.Notice.RANDOM_LOCKED -> R.string.notice_random_locked_unknown
    WatchFlow.Notice.ALREADY_IN_BATTLE -> R.string.notice_already_in_battle
    WatchFlow.Notice.QUEUE_UNAVAILABLE -> R.string.notice_queue_unavailable
    WatchFlow.Notice.DISTANCE_INVALID -> R.string.notice_distance_invalid
    WatchFlow.Notice.NO_RECORD -> R.string.notice_no_record
    WatchFlow.Notice.CELEBRITY_REMOVED -> R.string.notice_celebrity_removed
    WatchFlow.Notice.CELEB_OFFLINE -> R.string.notice_celeb_offline
    WatchFlow.Notice.RETRY_LATER -> R.string.notice_queue_unavailable
    WatchFlow.Notice.START_FAILED -> R.string.notice_start_failed
    WatchFlow.Notice.MEASUREMENT_STOPPED -> R.string.notice_measurement_stopped
    WatchFlow.Notice.NOT_SAVED -> R.string.notice_not_saved
    WatchFlow.Notice.HOLD_TO_END -> R.string.hold_to_end
    WatchFlow.Notice.LINK_LOST -> R.string.link_lost
    WatchFlow.Notice.PHONE_NOT_CONNECTED -> R.string.phone_not_connected
}

/**
 * 화면 위쪽(거리 위)에 잠깐 띄우는 문구 띠(안내 문구·"역전!", FR-254). 없으면(null) 그리지 않는다. 모션(W41): 생기면 위에서
 * 살짝 내려오며 커지고(320ms), 없어지면 흐려지며 사라지고(220ms), 문구나 색이 바뀌면 겹쳐 바뀐다. 처음 그릴 때 이미 있으면 움직이지 않는다.
 */
@Composable
internal fun BoxScope.AnimatedBanner(banner: Pair<String, Color>?, modifier: Modifier = Modifier) {
    val still = LocalMotionStill.current
    AnimatedContent(
        targetState = banner,
        modifier = modifier.align(Alignment.TopCenter),
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            when {
                still -> EnterTransition.None togetherWith ExitTransition.None

                initialState == null ->
                    (
                        fadeIn(tween(CAROUSEL_DURATION_MS, easing = CarouselEasing)) +
                            scaleIn(tween(CAROUSEL_DURATION_MS, easing = CarouselEasing), BANNER_START_SCALE) +
                            slideInVertically(tween(CAROUSEL_DURATION_MS, easing = CarouselEasing)) { -it / 2 }
                        ) togetherWith ExitTransition.None

                else -> fadeIn(tween(SCREEN_FADE_MS, easing = ScreenEasing)) togetherWith
                    fadeOut(tween(SCREEN_FADE_MS, easing = ScreenEasing))
            } using SizeTransform(clip = false)
        },
        label = "banner"
    ) { shown ->
        if (shown != null) BannerPill(shown.first, shown.second)
    }
}

@Composable
private fun BannerPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(top = 34.dp, start = 18.dp, end = 18.dp)
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = labelStyle(color = WatchColors.Background, size = 14.sp).copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center
    )
}

/** 띠가 나타날 때 처음 크기. */
private const val BANNER_START_SCALE = 0.8f

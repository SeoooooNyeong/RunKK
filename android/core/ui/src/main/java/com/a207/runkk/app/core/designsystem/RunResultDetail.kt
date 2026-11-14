package com.a207.runkk.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 러닝 결과 상세 한 장의 값(D-282). 자유런 결과(B-6)가 그리고, 대결 결과 2쪽(상세, D-283)도 같은 것을 그린다.
 * [dateLine]은 부르는 쪽이 만든 "10.16 목 · 오전 7:12 · 자유런"이다. [rest]는 정지 구간 합("휴식 n분 n초")이고 없으면 줄이 없다.
 * [crewLine]은 크루 반영 줄이고 CREW가 닫혀 있으면(1차 배포) 그리지 않는다. 가장 빠른 1km·소모 열량·날씨는 싣지 않는다(D-26).
 * [expression]은 뛰어오르는 아바타가 기본이고(D84, 피그마 B-6·C-8) 패배 상세만 부르는 쪽이 SAD를 넘긴다.
 * [faceKey]·[faceLayers]는 내 아바타 얼굴(크루런·대결 결과 상세 F-16b)이고 아바타 꾸미기가 열려 있을 때 그린다.
 * [dateItems]가 있으면 날짜 줄을 가운뎃점 글자([dateLine]) 대신 아이콘 메타 줄(MetaRow, [임시:D-성-10])로 그린다.
 */
@Immutable
data class RunResultDetailState(
    val distance: Distance,
    val dateLine: String,
    val elapsed: Duration,
    val rest: Duration? = null,
    val averagePace: Pace? = null,
    val averageHeartRate: Int? = null,
    val splitPaces: List<Pace> = emptyList(),
    val heartRateZones: List<Pair<String, Duration>> = emptyList(),
    val newRecords: List<String> = emptyList(),
    val crewLine: String? = null,
    val rewardPoints: Int? = null,
    val expression: AvatarExpression = AvatarExpression.JUMP,
    /** 내 아바타 얼굴 키·부위 그림(홈과 같은 출처). 부르는 쪽이 모르면 null·빈 목록이고 기본 아바타다. */
    val faceKey: String? = null,
    val faceLayers: List<FaceLayer> = emptyList(),
    val dateItems: List<MetaItem> = emptyList()
)

/**
 * 러닝 결과 상세(D-282, 피그마 B-6): 큰 거리와 날짜 줄, 기본 아바타, "시간"(아래 휴식)·"평균 페이스"·"평균 심박" 세 칸,
 * 구간 페이스(1km 막대)와 심박 구간(워치), 하이라이트 줄(★ 신기록, ♥ 크루 반영(CREW), + 리워드 합계).
 * 화면 제목·버튼은 부르는 쪽(ui.crew 크루런 결과 F-16b, ui.battle 결과 2쪽)이 그린다. 값은 피그마 F-16b(S15P31A207-293,
 * [임시:D-성-15]): 블록 사이 14, 큰 거리 64 Bold + km 22, 테두리 1 divider 카드(통계 모서리 24, 구간 26).
 * [avatarSlot]을 넘기면 아바타 칸을 그것으로 그린다(칸 [Modifier], 못 그릴 때의 지금 그림). 대결 결과는 내 전신 정지 그림을 넘긴다
 * (S15P31A207-300). 없으면 지금 그림이다.
 */
@Composable
fun RunResultDetail(
    state: RunResultDetailState,
    modifier: Modifier = Modifier,
    avatarSlot: (@Composable (Modifier, @Composable () -> Unit) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DistanceHeader(state, avatarSlot)
        StatCards(state)
        if (state.splitPaces.isNotEmpty() || state.heartRateZones.isNotEmpty()) PaceAndHeartCard(state)
        Highlights(state)
    }
}

/** 큰 거리 「4.87 km」(64 Bold, km 22 Bold, 글자 아래선 맞춤)와 날짜 줄(12 본문색 또는 메타 줄), 오른쪽 위 아바타 110. */
@Composable
private fun DistanceHeader(
    state: RunResultDetailState,
    avatarSlot: (@Composable (Modifier, @Composable () -> Unit) -> Unit)?
) {
    val spoken = stringResource(R.string.result_detail_distance_spoken, RunkkFormat.km(state.distance))
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                Text(
                    RunkkFormat.km(state.distance),
                    style = RunkkTextStyles.display(DETAIL_DISTANCE_SIZE, letterSpacingEm = DETAIL_DISTANCE_TRACKING),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    stringResource(R.string.result_detail_km),
                    style = RunkkTextStyles.text(22, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(start = 6.dp).alignByBaseline()
                )
            }
            if (state.dateItems.isNotEmpty()) {
                MetaRow(state.dateItems)
            } else {
                Text(state.dateLine, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
            }
        }
        if (state.expression == AvatarExpression.JUMP) {
            CheeringAvatar(faceKey = state.faceKey, faceLayers = state.faceLayers, avatarSlot = avatarSlot)
        } else {
            AvatarOrSlot(Modifier.size(110.dp), avatarSlot) { slotModifier ->
                RunkkAvatar(
                    faceKey = state.faceKey,
                    expression = state.expression,
                    modifier = slotModifier,
                    faceLayers = state.faceLayers,
                    colorFilter = sceneAvatarFilter()
                )
            }
        }
    }
}

/**
 * [avatarSlot]이 있으면 [modifier] 칸 안을 그것으로 채우고(못 그리면 [current]), 없으면 [current]를 [modifier]로 그린다.
 * 칸이 지금 그림을 칸 안에 다시 그려도 위치·크기가 두 번 걸리지 않게 칸 안쪽은 꽉 채운다.
 */
@Composable
private fun AvatarOrSlot(
    modifier: Modifier,
    avatarSlot: (@Composable (Modifier, @Composable () -> Unit) -> Unit)?,
    current: @Composable (Modifier) -> Unit
) {
    if (avatarSlot == null) {
        current(modifier)
    } else {
        Box(modifier) { avatarSlot(Modifier.fillMaxSize()) { current(Modifier.fillMaxSize()) } }
    }
}

/**
 * 기뻐하는 결과 아바타: 서 있다가 뛰어오르기를 계속 되풀이한다(사용자 요청 2026-10-30). 뛰는 모양은 홈 아바타 탭과 같다
 * (오르기 tween, 내리기 spring). 움직임 줄이기면 뛰어오른 그림에 멈춰 있다. [avatarSlot](전신 정지 그림)이면 그 그림이 통째로 뛴다.
 */
@Composable
private fun CheeringAvatar(
    modifier: Modifier = Modifier.size(110.dp),
    faceKey: String? = null,
    faceLayers: List<FaceLayer> = emptyList(),
    avatarSlot: (@Composable (Modifier, @Composable () -> Unit) -> Unit)? = null
) {
    val reduceMotion = LocalReduceMotion.current
    val hop = remember { Animatable(0f) }
    var isJumping by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(CHEER_STAND_MILLIS)
            isJumping = true
            hop.animateTo(1f, tween(durationMillis = CHEER_UP_MILLIS, easing = FastOutSlowInEasing))
            hop.animateTo(0f, spring(dampingRatio = CHEER_DAMPING, stiffness = Spring.StiffnessMediumLow))
            isJumping = false
        }
    }
    AvatarOrSlot(modifier.graphicsLayer { translationY = -hop.value * CHEER_HOP_HEIGHT.toPx() }, avatarSlot) {
        RunkkAvatar(
            faceKey = faceKey,
            expression = if (isJumping) AvatarExpression.JUMP else AvatarExpression.STAND,
            modifier = it,
            faceLayers = faceLayers,
            colorFilter = sceneAvatarFilter()
        )
    }
}

private const val CHEER_STAND_MILLIS = 900L
private const val CHEER_UP_MILLIS = 240
private const val CHEER_DAMPING = 0.55f
private val CHEER_HOP_HEIGHT = 22.dp

/** 세 칸: 시간(아래 휴식), 평균 페이스, 평균 심박. */
@Composable
private fun StatCards(state: RunResultDetailState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            label = stringResource(R.string.result_detail_time),
            value = RunkkFormat.duration(state.elapsed),
            spoken = RunkkFormat.durationSpoken(state.elapsed),
            caption = state.rest?.let { restLabel(it) },
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.result_detail_avg_pace),
            value = RunkkFormat.pace(state.averagePace),
            spoken = RunkkFormat.paceSpoken(state.averagePace),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = stringResource(R.string.result_detail_avg_heart),
            value = state.averageHeartRate?.toString() ?: RunkkFormat.EMPTY,
            modifier = Modifier.weight(1f)
        )
    }
}

/** "휴식 n분 n초". */
@Composable
private fun restLabel(rest: Duration): String =
    stringResource(R.string.result_detail_rest, rest.toMinutes(), rest.seconds % SECONDS_PER_MINUTE)

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    spoken: String? = null,
    caption: String? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                spoken?.let { contentDescription = listOfNotNull(label, it, caption).joinToString(", ") }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = RunkkTextStyles.text(11, lineHeight = 13), color = RunkkColors.TextPrimary)
        Text(value, style = RunkkTextStyles.text(18, FontWeight.Bold, lineHeight = 22), color = RunkkColors.TextPrimary)
        caption?.let { Text(it, style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary) }
    }
}

/** 구간 페이스(1km마다 막대와 6'20")와 심박 구간(워치) 한 카드. 빠른 구간일수록 막대가 길다. */
@Composable
private fun PaceAndHeartCard(state: RunResultDetailState) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (state.splitPaces.isNotEmpty()) {
            Text(
                stringResource(R.string.result_detail_split_title),
                style = CARD_SECTION_TITLE,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            val fastest = state.splitPaces.minOf { it.secondsPerKm }.coerceAtLeast(1)
            state.splitPaces.forEachIndexed { index, pace ->
                SplitRow(index + 1, pace, ratio = fastest.toFloat() / pace.secondsPerKm.coerceAtLeast(1))
            }
        }
        if (state.heartRateZones.isNotEmpty()) HeartZones(state.heartRateZones)
    }
}

@Composable
private fun SplitRow(km: Int, pace: Pace, ratio: Float) {
    val label = stringResource(R.string.result_detail_split_km, km)
    val value = splitPace(pace)
    val spoken = stringResource(R.string.result_detail_split_spoken, km, RunkkFormat.paceSpoken(pace))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(min = 18.dp).clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(label, style = RunkkTextStyles.text(12), color = RunkkColors.TextPrimary, modifier = Modifier.width(34.dp))
        Box(
            Modifier
                .weight(1f)
                .height(9.dp)
                .clip(CircleShape)
                .background(RunkkColors.SurfaceHigh)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(MIN_BAR, 1f))
                    .clip(CircleShape)
                    .background(splitBarFill(LocalRunkkColors.current))
            )
        }
        Text(
            value,
            style = RunkkTextStyles.text(12, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

/**
 * 구간 페이스 막대 채움(WCAG 1.4.11, 리뷰 PL12-A2): 트랙(surfaceHigh)과 카드 바탕 대비 3:1 이상인 무채색 [RunkkColors.textSecondary]
 * (라이트 #52525B 약 6.1:1, 다크 #A1A1AA로 이전 barCrew 다크 값과 같음). 크루 막대 토큰 barCrew는 라이트 #D4D4D8이 트랙 #E4E4E7과
 * 약 1.16:1이라 여기서 쓰지 않는다. ColorContrastTest가 두 벌 모두 지킨다.
 */
internal fun splitBarFill(colors: RunkkColors): Color = colors.textSecondary

/** 심박 구간: 제목, "워치" 표시, 구간 비율 막대. 구간별 시간은 보조 기술에 읽어 준다. */
@Composable
private fun HeartZones(zones: List<Pair<String, Duration>>) {
    val spoken = stringResource(
        R.string.result_detail_zone_spoken,
        zones.joinToString { (zone, d) -> "$zone ${d.toMinutes()}" }
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            stringResource(R.string.result_detail_zone_title),
            style = CARD_SECTION_TITLE,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Box(
            Modifier
                .heightIn(min = 24.dp)
                .border(1.dp, RunkkColors.Border, CircleShape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.result_detail_zone_watch),
                style = RunkkTextStyles.Micro,
                color = RunkkColors.TextPrimary
            )
        }
    }
    val total = zones.sumOf { it.second.seconds }.coerceAtLeast(1)
    val colors = zoneColors()
    RunkkSegmentBar(
        segments = zones.mapIndexed { i, (_, d) ->
            d.seconds.toFloat() / total to
                colors[i.coerceAtMost(colors.lastIndex)]
        },
        modifier = Modifier.semantics { contentDescription = spoken },
        height = ZONE_BAR_HEIGHT
    )
}

/** 하이라이트 줄: ★ 신기록, ♥ 크루 반영(CREW가 열렸을 때만), + 리워드 합계 한 줄. */
@Composable
private fun Highlights(state: RunResultDetailState) {
    val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.newRecords.forEach { HighlightRow(RunkkIcons.Star, it, hasCircle = true) }
        state.crewLine?.takeIf { isCrewOpen }?.let { HighlightRow(RunkkIcons.Heart, it, hasCircle = true) }
        state.rewardPoints?.let {
            HighlightRow(RunkkIcons.Plus, stringResource(R.string.result_detail_reward, it), hasCircle = false)
        }
    }
}

/** 하이라이트 한 줄(F-16b): 원 26(surfaceHigh) 안 아이콘 12, 또는 원 없이 아이콘 24(「+」 리워드), 사이 8, 글자 13. */
@Composable
private fun HighlightRow(icon: ImageVector, text: String, hasCircle: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(HIGHLIGHT_MARK)
                .then(if (hasCircle) Modifier.clip(CircleShape).background(RunkkColors.SurfaceHigh) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = RunkkColors.TextPrimary,
                modifier = Modifier.size(if (hasCircle) 12.dp else 24.dp)
            )
        }
        Text(text, style = RunkkTextStyles.text(13), color = RunkkColors.TextPrimary)
    }
}

/** 구간 페이스 표기 6'20". */
fun splitPace(pace: Pace): String = String.format(
    Locale.US,
    "%d'%02d\"",
    pace.secondsPerKm / SECONDS_PER_MINUTE,
    pace.secondsPerKm % SECONDS_PER_MINUTE
)

/** 심박 구간 막대 색(낮은 구간부터). */
@Composable
private fun zoneColors(): List<Color> = listOf(
    RunkkColors.BorderStrong,
    RunkkColors.BorderHover,
    RunkkColors.LimeDim,
    RunkkColors.Lime,
    RunkkColors.Lavender
)

private const val SECONDS_PER_MINUTE = 60L
private const val MIN_BAR = 0.08f
private const val DETAIL_DISTANCE_SIZE = 64

/** 피그마 자간 -0.5px(64 글자) = -0.0078em. */
private const val DETAIL_DISTANCE_TRACKING = -0.0078f
private val ZONE_BAR_HEIGHT = 16.dp
private val HIGHLIGHT_MARK = 26.dp

/** 결과 상세 카드 안 제목(구간 페이스·심박 구간): 14 Bold, 줄 높이 21. */
private val CARD_SECTION_TITLE = RunkkTextStyles.text(14, FontWeight.Bold, lineHeight = 21)

// ---------- 러닝 결과 상용안 공용 부품(FR-289, D-502, 피그마 61326:1878·61325:2008) ----------
// 러닝 결과(ui.run)와 배치고사 판 결과(ui.battle)가 함께 쓴다. 위의 RunResultDetail(대결 결과 2쪽)은 그대로 둔다.

/**
 * 결과 위쪽 거리 히어로(피그마 61326:1878): 종류 칩(없으면 생략) + 날짜 줄, 큰 거리 60 Bold + 「km」, 오른쪽 기뻐하는 아바타 84.
 * 큰 숫자는 Barlow Condensed 기울임이다(prod 글꼴, 2026-11-12). 아바타 얼굴은 [faceKey]·[faceLayers](내 아바타)이고 없으면 기본 얼굴이다.
 * [avatarSlot]을 넘기면 아바타 칸을 그것으로 그린다(배치고사 판 결과는 내 전신 정지 그림, S15P31A207-300).
 */
@Composable
fun ResultDistanceHero(
    distance: Distance,
    dateLine: String,
    kindLabel: String?,
    modifier: Modifier = Modifier,
    faceKey: String? = null,
    faceLayers: List<FaceLayer> = emptyList(),
    avatarSlot: (@Composable (Modifier, @Composable () -> Unit) -> Unit)? = null
) {
    val km = RunkkFormat.km(distance)
    val spoken = stringResource(
        R.string.result_hero_spoken,
        listOfNotNull(kindLabel, dateLine.takeIf { it.isNotBlank() }).joinToString(", "),
        km
    )
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                kindLabel?.let {
                    Text(
                        it,
                        style = RunkkTextStyles.text(11, FontWeight.Medium),
                        color = RunkkColors.OnLimeFill,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RunkkColors.LimeFill)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(dateLine, style = RunkkTextStyles.text(12, FontWeight.Medium), color = RunkkColors.TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    km,
                    style = RunkkTextStyles.text(
                        HERO_SIZE,
                        FontWeight.Bold,
                        lineHeight = HERO_SIZE,
                        letterSpacingEm = HERO_TRACKING
                    ),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    stringResource(R.string.result_detail_km),
                    style = RunkkTextStyles.text(20, FontWeight.Medium),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        CheeringAvatar(Modifier.size(HERO_AVATAR), faceKey = faceKey, faceLayers = faceLayers, avatarSlot = avatarSlot)
    }
}

/**
 * 지표 카드(피그마 61325:2008): 시간(아래 「휴식 n분 n초」)·평균 페이스·평균 심박 세 칸을 같은 폭으로, 심박이 없으면 두 칸.
 * 카드 안 아래에 [sections](SplitPaceSection·HeartZoneSection, 각자 위 구분선을 그린다)를 이어 그린다.
 */
@Composable
fun RunMetricCard(
    elapsed: Duration?,
    paused: Duration?,
    averagePace: Pace?,
    averageHeartRate: Int?,
    modifier: Modifier = Modifier,
    sections: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .resultCard()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            MetricCell(
                label = stringResource(R.string.result_detail_time),
                value = elapsed?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY,
                unit = null,
                spoken = elapsed?.let { RunkkFormat.durationSpoken(it) },
                caption = paused?.takeIf { !it.isZero }?.let { restLabel(it) },
                modifier = Modifier.weight(1f)
            )
            MetricDivider()
            MetricCell(
                label = stringResource(R.string.result_detail_avg_pace),
                value = averagePace?.let(::splitPace) ?: RunkkFormat.EMPTY,
                unit = stringResource(R.string.result_metric_pace_unit),
                spoken = RunkkFormat.paceSpoken(averagePace),
                modifier = Modifier.weight(1f).padding(start = 14.dp)
            )
            if (averageHeartRate != null) {
                MetricDivider()
                MetricCell(
                    label = stringResource(R.string.result_detail_avg_heart),
                    value = averageHeartRate.toString(),
                    unit = stringResource(R.string.result_metric_heart_unit),
                    spoken = null,
                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
            }
        }
        sections()
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    unit: String?,
    spoken: String?,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    val description = listOfNotNull(label, spoken ?: listOfNotNull(value, unit).joinToString(" "), caption)
        .joinToString(", ")
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = RunkkTextStyles.text(22, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            unit?.let {
                Text(
                    it,
                    style = RunkkTextStyles.text(12, FontWeight.Medium),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        caption?.let { Text(it, style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary) }
    }
}

/** 지표 칸 사이 세로 구분선 1×44. */
@Composable
private fun MetricDivider() {
    Box(Modifier.width(1.dp).height(44.dp).background(RunkkColors.Border))
}

/** 카드 안 구역 위 가로 구분선. */
@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(RunkkColors.Border))
}

/**
 * 구간 페이스(지표 카드 안): 머리 「구간 페이스」 + 범례(보라 점 「가장 빠른 구간 n km」), km마다 가로 막대와 페이스.
 * 가장 빠른 구간(최소 초, 같으면 앞 구간)은 막대가 보라(Violet)이고 숫자는 라이트 Violet·다크 Lavender Bold다(8절 16).
 * 다른 구간 막대는 TextSecondary이고 길이는 가장 빠른 구간 대비 비율이다. 구간이 없으면 아무것도 그리지 않는다.
 */
@Composable
fun SplitPaceSection(splits: List<Pace>, modifier: Modifier = Modifier) {
    val fastest = fastestSplitIndex(splits) ?: return
    val fastestSeconds = splits[fastest].secondsPerKm.coerceAtLeast(1)
    val accentText = if (LocalRunkkColors.current.isDark) RunkkColors.Lavender else RunkkColors.Violet
    Column(modifier = modifier.fillMaxWidth()) {
        SectionDivider()
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.result_detail_split_title),
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                Box(Modifier.size(8.dp).clip(CircleShape).background(RunkkColors.Violet))
                Text(
                    stringResource(R.string.result_split_fastest, fastest + 1),
                    style = RunkkTextStyles.text(11, FontWeight.Medium),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            splits.forEachIndexed { index, pace ->
                val isFastest = index == fastest
                SplitBar(
                    km = index + 1,
                    pace = pace,
                    ratio = fastestSeconds.toFloat() / pace.secondsPerKm.coerceAtLeast(1),
                    isFastest = isFastest,
                    accentText = accentText
                )
            }
        }
    }
}

@Composable
private fun SplitBar(km: Int, pace: Pace, ratio: Float, isFastest: Boolean, accentText: Color) {
    val spoken = if (isFastest) {
        stringResource(R.string.result_split_spoken_fastest, km, RunkkFormat.paceSpoken(pace))
    } else {
        stringResource(R.string.result_detail_split_spoken, km, RunkkFormat.paceSpoken(pace))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(
            stringResource(R.string.result_detail_split_km, km),
            style = RunkkTextStyles.text(12, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.width(34.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(RunkkColors.Border)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(MIN_BAR, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isFastest) RunkkColors.Violet else RunkkColors.TextSecondary)
            )
        }
        Text(
            splitPace(pace),
            style = RunkkTextStyles.text(13, if (isFastest) FontWeight.Bold else FontWeight.Medium),
            color = if (isFastest) accentText else RunkkColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

/** 가장 빠른 구간의 번호(0부터): 초가 가장 작은 구간, 같으면 앞 구간. 구간이 없으면 null(TC-5826). */
fun fastestSplitIndex(splits: List<Pace>): Int? = splits.indices.minByOrNull { splits[it].secondsPerKm }

/**
 * 심박 구간(지표 카드 안, J24-17): 머리 「심박 구간」 + 출처 칩(「워치」, [source]가 있을 때), 4단계 누적 막대(Z1 Border·Z2 LimeFill·
 * Z3 연보라·Z4 Violet)와 범례 「Zn nn%」. 구간이 없으면 막대 대신 안내: 출생연도가 없으면(심박은 있는데 최대 심박을 몰라 구간을 못 냄)
 * 「출생연도를 입력하면…」, 아니면 「워치를 연결하면…」. [source]는 서버 출처 이름이고 지금은 "WATCH"만 있다.
 */
@Composable
fun HeartZoneSection(
    zones: Map<String, Duration>,
    source: String?,
    hasBirthYear: Boolean,
    modifier: Modifier = Modifier
) {
    val bands = HeartZoneBands.toFour(zones)
    Column(modifier = modifier.fillMaxWidth()) {
        SectionDivider()
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.result_detail_zone_title),
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                if (source != null) {
                    Text(
                        stringResource(R.string.result_detail_zone_watch),
                        style = RunkkTextStyles.text(11, FontWeight.Medium),
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier
                            .border(1.dp, RunkkColors.Border, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            if (bands.isEmpty()) {
                Text(
                    stringResource(
                        if (hasBirthYear) R.string.result_zone_no_watch else R.string.result_zone_no_birth_year
                    ),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            } else {
                ZoneBar(bands)
            }
        }
    }
}

@Composable
private fun ZoneBar(bands: List<ZoneShare>) {
    val colors = fourZoneColors()
    val labels = bands.map { stringResource(R.string.result_zone_share, it.label, it.percent) }
    val spoken = stringResource(R.string.result_zone_spoken, labels.joinToString(", "))
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val shown = bands.withIndex().filter { it.value.percent > 0 }
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEachIndexed { position, (index, band) ->
                val start = if (position == 0) ZONE_END else 0.dp
                val end = if (position == shown.lastIndex) ZONE_END else 0.dp
                Box(
                    Modifier
                        .weight(band.percent.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                        .background(colors[index])
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index]))
                    Text(label, style = RunkkTextStyles.text(11, FontWeight.Medium), color = RunkkColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * 4단계 막대 색(7.1절): Z1 Border, Z2 LimeFill, Z3 연보라, Z4 Violet. 라이트 Lavender는 Violet과 같은 색이라 Z3는 두 벌 모두
 * 피그마 #9D94F0에 가까운 다크 벌 Lavender(#9C92F0)로 고정한다.
 */
@Composable
private fun fourZoneColors(): List<Color> =
    listOf(RunkkColors.Border, RunkkColors.LimeFill, RunkkColors.dark.lavender, RunkkColors.Violet)

/**
 * 이번 러닝 성과 카드(피그마 61326:1878): 행마다 아이콘 원 24 + 이름 13 + 값 13, 둘째 행부터 위 구분선. 크루 행은 크루가 열려 있을 때만
 * (1차 배포 CREW 닫힘). 그릴 행이 없으면 아무것도 그리지 않는다(빈 행을 그리지 않는다, 8절 9).
 */
@Composable
fun AchievementCard(rows: List<AchievementRow>, modifier: Modifier = Modifier) {
    val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
    val shown = rows.filter { it.icon != AchievementIcon.CREW || isCrewOpen }
    if (shown.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .resultCard()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        shown.forEachIndexed { index, row ->
            if (index > 0) SectionDivider()
            AchievementLine(row)
        }
    }
}

@Composable
private fun AchievementLine(row: AchievementRow) {
    val label = row.label.asString()
    val icon = when (row.icon) {
        AchievementIcon.STAR -> RunkkIcons.Star
        AchievementIcon.CREW -> RunkkIcons.Users
        AchievementIcon.REWARD -> RunkkIcons.Plus
    }
    val isCrew = row.icon == AchievementIcon.CREW
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "$label, ${row.value}" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (isCrew) RunkkColors.Violet else RunkkColors.LimeFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isCrew) Color.White else RunkkColors.OnLimeFill,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(row.value, style = RunkkTextStyles.text(13, FontWeight.Medium), color = RunkkColors.TextSecondary)
    }
}

/**
 * 결과 하단 고정 바(피그마 61326:1878): 표면, 위쪽 둥근 20, 주 버튼 52(라임, 「홈으로」·「다음 판 뛰기」)과 글자 버튼 40
 * (「기록 카드 만들기」·「홈으로」). 화면은 스크롤 영역 밖 아래에 둔다. [isSecondaryEnabled]가 거짓이면 글자 버튼을 누를 수 없다
 * (기록 카드는 서버에 올라간 뒤 연다).
 */
@Composable
fun ResultBottomBar(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    isSecondaryEnabled: Boolean = true
) {
    val top = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(CARD_SHADOW, top, ambientColor = SHADOW, spotColor = SHADOW)
            .clip(top)
            .background(RunkkColors.Surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(CircleShape)
                .background(RunkkColors.LimeFill)
                .clickable(role = Role.Button, onClick = onPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(primary, style = RunkkTextStyles.text(14, FontWeight.Medium), color = RunkkColors.OnLimeFill)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .alpha(if (isSecondaryEnabled) 1f else DISABLED_ALPHA)
                .clickable(enabled = isSecondaryEnabled, role = Role.Button, onClick = onSecondary)
                // 글자 버튼은 바탕이 없어 터치 영역을 48로 둔다(보이는 글자 자리는 40, PL1-A2)
                .minimumInteractiveComponentSize()
                .heightIn(min = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(secondary, style = RunkkTextStyles.text(14, FontWeight.Medium), color = RunkkColors.TextPrimary)
        }
    }
}

/** 결과 카드 바탕: 그림자 + 둥근 18 + 표면. */
@Composable
private fun Modifier.resultCard(): Modifier {
    val shape = RoundedCornerShape(CARD_CORNER)
    return shadow(CARD_SHADOW, shape, ambientColor = SHADOW, spotColor = SHADOW)
        .clip(shape)
        .background(RunkkColors.Surface)
}

private const val HERO_SIZE = 60
private const val HERO_TRACKING = -0.03f
private val HERO_AVATAR = 84.dp
private val CARD_CORNER = 18.dp
private val CARD_SHADOW = 6.dp
private val ZONE_END = 5.dp
private const val DISABLED_ALPHA = 0.4f

/** 카드·바 그림자 색(피그마 rgba(23,26,18,0.08~0.16) 근사). */
private val SHADOW = Color(0x29171A12)

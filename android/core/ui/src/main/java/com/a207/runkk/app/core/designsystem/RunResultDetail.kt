package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.util.Locale

/**
 * 러닝 결과 상세 한 장의 값(D-282). 자유런 결과(B-6)가 그리고, 대결 결과 2쪽(상세, D-283)도 같은 것을 그린다.
 * [dateLine]은 부르는 쪽이 만든 "10.16 목 · 오전 7:12 · 자유런"이다. [rest]는 정지 구간 합("휴식 n분 n초")이고 없으면 줄이 없다.
 * [crewLine]은 크루 반영 줄이고 CREW가 닫혀 있으면(1차 배포) 그리지 않는다. 가장 빠른 1km·소모 열량·날씨는 싣지 않는다(D-26).
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
    val expression: AvatarExpression = AvatarExpression.JOY
)

/**
 * 러닝 결과 상세(D-282, 피그마 B-6): 큰 거리와 날짜 줄, 기본 아바타, "시간"(아래 휴식)·"평균 페이스"·"평균 심박" 세 칸,
 * 구간 페이스(1km 막대)와 심박 구간(워치), 하이라이트 줄(★ 신기록, ♥ 크루 반영(CREW), + 리워드 합계).
 * 화면 제목·버튼은 부르는 쪽(ui.run 결과, ui.battle 결과 2쪽)이 그린다.
 */
@Composable
fun RunResultDetail(state: RunResultDetailState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DistanceHeader(state)
        StatCards(state)
        if (state.splitPaces.isNotEmpty() || state.heartRateZones.isNotEmpty()) PaceAndHeartCard(state)
        Highlights(state)
    }
}

/** 큰 거리 "5.21 km"와 날짜 줄, 오른쪽 위 아바타. */
@Composable
private fun DistanceHeader(state: RunResultDetailState) {
    val spoken = stringResource(R.string.result_detail_distance_spoken, RunkkFormat.km(state.distance))
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
            ) {
                Text(
                    RunkkFormat.km(state.distance),
                    style = RunkkTextStyles.DisplayLarge,
                    color = RunkkColors.TextPrimary
                )
                Text(
                    stringResource(R.string.result_detail_km),
                    style = RunkkTextStyles.text(20, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
            }
            Text(state.dateLine, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        RunkkAvatar(faceKey = null, expression = state.expression, modifier = Modifier.size(104.dp))
    }
}

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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Surface)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                spoken?.let { contentDescription = listOfNotNull(label, it, caption).joinToString(", ") }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Text(value, style = RunkkTextStyles.text(18, FontWeight.Black), color = RunkkColors.TextPrimary)
        caption?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
    }
}

/** 구간 페이스(1km마다 막대와 6'20")와 심박 구간(워치) 한 카드. 빠른 구간일수록 막대가 길다. */
@Composable
private fun PaceAndHeartCard(state: RunResultDetailState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RunkkColors.Surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.splitPaces.isNotEmpty()) {
            Text(
                stringResource(R.string.result_detail_split_title),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary
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
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(label, style = RunkkTextStyles.Label, color = RunkkColors.TextPrimary, modifier = Modifier.width(44.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(RunkkColors.Border)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(MIN_BAR, 1f))
                    .clip(CircleShape)
                    .background(RunkkColors.Lime)
            )
        }
        Text(
            value,
            style = RunkkTextStyles.text(14, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** 심박 구간: 제목, "워치" 표시, 구간 비율 막대. 구간별 시간은 보조 기술에 읽어 준다. */
@Composable
private fun HeartZones(zones: List<Pair<String, Duration>>) {
    val spoken = stringResource(
        R.string.result_detail_zone_spoken,
        zones.joinToString { (zone, d) -> "$zone ${d.toMinutes()}" }
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            stringResource(R.string.result_detail_zone_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.result_detail_zone_watch),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(RunkkColors.Background)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
    val total = zones.sumOf { it.second.seconds }.coerceAtLeast(1)
    val colors = zoneColors()
    RunkkSegmentBar(
        segments = zones.mapIndexed { i, (_, d) ->
            d.seconds.toFloat() / total to
                colors[i.coerceAtMost(colors.lastIndex)]
        },
        modifier = Modifier.semantics { contentDescription = spoken }
    )
}

/** 하이라이트 줄: ★ 신기록, ♥ 크루 반영(CREW가 열렸을 때만), + 리워드 합계 한 줄. */
@Composable
private fun Highlights(state: RunResultDetailState) {
    val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.newRecords.forEach { HighlightRow(RunkkIcons.Star, it) }
        state.crewLine?.takeIf { isCrewOpen }?.let { HighlightRow(RunkkIcons.Heart, it) }
        state.rewardPoints?.let { HighlightRow(RunkkIcons.Plus, stringResource(R.string.result_detail_reward, it)) }
    }
}

@Composable
private fun HighlightRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(RunkkColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.TextPrimary, modifier = Modifier.size(14.dp))
        }
        Text(text, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
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

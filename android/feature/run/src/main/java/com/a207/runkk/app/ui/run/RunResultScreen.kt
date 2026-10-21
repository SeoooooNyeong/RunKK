package com.a207.runkk.app.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkLogo
import com.a207.runkk.app.core.designsystem.RunkkLogoKind
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 러닝 결과·기록 카드(B4). "기록 카드"는 ui.record의 카드 팝업으로, "홈으로"는 홈으로 간다. */
@Composable
fun RunResultRoute(recordId: String, viewModel: RunResultViewModel = hiltViewModel()) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RunResultScreen(
        state = state,
        onCard = { navigator.navigate(Routes.RecordCard(recordId)) },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(recordId, force = true) }
    )
}

@Composable
fun RunResultScreen(
    state: RunResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(state)
            when {
                state.distance == null && state.loading -> Box(Modifier.fillMaxWidth().height(240.dp)) {
                    RunkkLoading()
                }

                state.distance == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.run_result_not_found),
                    onRetry = onRetry
                )

                else -> ResultBody(state)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkSecondaryButton(
                stringResource(R.string.run_result_card),
                onClick = onCard,
                enabled = state.uploaded,
                modifier = Modifier.weight(1f)
            )
            RunkkPrimaryButton(
                stringResource(R.string.run_result_home),
                onClick = onHome,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Header(state: RunResultUiState) {
    val datePattern = stringResource(R.string.run_result_date_pattern)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RunkkLogo(RunkkLogoKind.Icon, height = 24.dp)
        RunkkBadge(kindLabel(state.cardKind))
        Text(
            text = listOfNotNull(state.startedAt?.let { dateLabel(it, datePattern) }, state.weather)
                .joinToString(" · "),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(if (state.uploaded) R.string.run_result_saved else R.string.run_result_sending),
            style = RunkkTextStyles.Caption,
            color = if (state.uploaded) RunkkColors.Lime else RunkkColors.TextSecondary
        )
    }
}

@Composable
private fun ResultBody(state: RunResultUiState) {
    state.newRecords.firstOrNull()?.let { record ->
        RunkkBanner(record, strong = true) {
            Icon(
                RunkkIcons.Trophy,
                contentDescription = null,
                tint = RunkkColors.OnLime,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    ResultDistance(state.distance ?: Distance.ZERO)
    ResultStats(state)
    if (state.calories != null) {
        Text(
            stringResource(R.string.run_result_calories, state.calories),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
    CourseCard(state.route)
    if (state.heartRateZones.isNotEmpty()) {
        HeartRateZoneCard(state)
    }
}

/** 큰 거리 숫자와 KM 단위. */
@Composable
private fun ResultDistance(distance: Distance) {
    val distanceSpoken = stringResource(R.string.run_distance_spoken, RunkkFormat.km(distance))
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = distanceSpoken
        }
    ) {
        Text(RunkkFormat.km(distance), style = RunkkTextStyles.DisplayLarge, color = RunkkColors.TextPrimary)
        Text(
            "KM",
            style = RunkkTextStyles.display(24),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/** 시간·평균 페이스·평균 심박 세 칸. */
@Composable
private fun ResultStats(state: RunResultUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.run_result_stat_time),
            StatTileValue(
                state.elapsed?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY,
                spoken = state.elapsed?.let {
                    RunkkFormat.durationSpoken(it)
                }
            ),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.run_stat_avg_pace),
            StatTileValue(RunkkFormat.pace(state.averagePace), spoken = RunkkFormat.paceSpoken(state.averagePace)),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.run_result_stat_heart_rate),
            StatTileValue(state.averageHeartRate?.toString() ?: RunkkFormat.EMPTY),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 코스 카드: 경로가 둘 이상이면 그림, 아니면 빈 칸과 "기록 없음". */
@Composable
private fun CourseCard(route: List<GeoPoint>) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        if (route.size >= 2) {
            RoutePreview(route, modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            stringResource(if (route.size >= 2) R.string.run_result_course else R.string.run_result_course_none),
            style = RunkkTextStyles.Micro,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 심박 구간 카드: 구간 비율 막대와 구간별 분. */
@Composable
private fun HeartRateZoneCard(state: RunResultUiState) {
    val zones = state.heartRateZones
    val zoneTexts = zones.map { (zone, d) -> stringResource(R.string.run_result_zone_item, zone, d.toMinutes()) }
    val distributionSpoken = stringResource(R.string.run_result_zone_spoken, zoneTexts.joinToString())
    RunkkCard {
        Row {
            Text(
                stringResource(R.string.run_result_zone_title),
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(
                    R.string.run_result_zone_watch_average,
                    state.averageHeartRate?.toString() ?: RunkkFormat.EMPTY
                ),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        val total = zones.sumOf { it.second.seconds }.coerceAtLeast(1)
        RunkkSegmentBar(
            segments = zones.mapIndexed { index, (_, duration) ->
                duration.seconds.toFloat() / total to
                    ZONE_COLORS[index % ZONE_COLORS.size]
            },
            modifier = Modifier.semantics { contentDescription = distributionSpoken }
        )
        Text(
            zoneTexts.joinToString("  "),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** 경로선을 칸에 맞춰 그린다(지도 없이). 시작은 흰 점, 끝은 라임 점. */
@Composable
internal fun RoutePreview(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    val routeDesc = stringResource(R.string.run_result_route_desc)
    Canvas(modifier = modifier.semantics { contentDescription = routeDesc }) {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val span = maxOf(maxLat - minLat, maxLon - minLon).takeIf { it > 0 } ?: 1.0
        val pad = 12.dp.toPx()
        val scale = minOf(size.width - 2 * pad, size.height - 2 * pad) / span
        val offsetX = (size.width - (maxLon - minLon) * scale) / 2
        val offsetY = (size.height - (maxLat - minLat) * scale) / 2
        fun toOffset(p: GeoPoint) = Offset(
            (offsetX + (p.longitude - minLon) * scale).toFloat(),
            (
                offsetY +
                    (maxLat - p.latitude) * scale
                ).toFloat()
        )
        val path = Path().apply {
            points.forEachIndexed { i, p ->
                toOffset(p).let { o ->
                    if (i ==
                        0
                    ) {
                        moveTo(o.x, o.y)
                    } else {
                        lineTo(o.x, o.y)
                    }
                }
            }
        }
        drawPath(
            path,
            RunkkColors.Lime,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(RunkkColors.TextPrimary, radius = 6.dp.toPx(), center = toOffset(points.first()))
        drawCircle(RunkkColors.Lime, radius = 6.dp.toPx(), center = toOffset(points.last()))
    }
}

@Composable
internal fun kindLabel(kind: CardKind?): String = stringResource(
    when (kind) {
        CardKind.FREE_RUN, null -> R.string.run_kind_free
        CardKind.BATTLE_RUN -> R.string.run_kind_battle
        CardKind.SELF_RUN -> R.string.run_kind_self
        CardKind.CELEB_RUN -> R.string.run_kind_celeb
        CardKind.CREW_RUN -> R.string.run_kind_crew
    }
)

/** [pattern](예: "M월 d일 (E)")으로 시작 시각의 날짜를 쓴다. */
internal fun dateLabel(at: Instant, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(at.atZone(ZoneId.systemDefault()))

private val ZONE_COLORS =
    listOf(
        RunkkColors.BorderStrong,
        RunkkColors.BorderHover,
        RunkkColors.LimeDim,
        RunkkColors.Lime,
        RunkkColors.Lavender
    )

@Preview(name = "B4 러닝 결과·카드", widthDp = 390, heightDp = 844)
@Composable
private fun RunResultPreview() {
    RunkkTheme(reduceMotion = true) {
        RunResultScreen(
            state = RunResultUiState(
                loading = false,
                recordId = "r",
                cardKind = CardKind.FREE_RUN,
                startedAt = Instant.parse("2026-10-20T10:00:00Z"),
                distance = Distance(5_020),
                elapsed = Duration.ofSeconds(1_668),
                averagePace = Pace(332),
                averageHeartRate = 152,
                route = listOf(
                    GeoPoint(37.50, 127.00),
                    GeoPoint(37.505, 127.01),
                    GeoPoint(37.502, 127.02),
                    GeoPoint(37.51, 127.03)
                ),
                weather = "12°C 맑음",
                newRecords = listOf("최장 거리 신기록 · 직전 4.80km"),
                heartRateZones = listOf(
                    "Z1" to Duration.ofMinutes(2),
                    "Z2" to Duration.ofMinutes(6),
                    "Z3" to Duration.ofMinutes(9),
                    "Z4" to Duration.ofMinutes(5),
                    "Z5" to Duration.ofMinutes(2)
                ),
                uploaded = true
            ),
            onCard = {},
            onHome = {},
            onRetry = {}
        )
    }
}

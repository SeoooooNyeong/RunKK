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

                state.distance == null -> RunkkErrorState(state.error ?: "기록을 찾지 못했어요", onRetry = onRetry)

                else -> ResultBody(state)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkSecondaryButton("기록 카드", onClick = onCard, enabled = state.uploaded, modifier = Modifier.weight(1f))
            RunkkPrimaryButton("홈으로", onClick = onHome, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Header(state: RunResultUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RunkkLogo(RunkkLogoKind.Icon, height = 24.dp)
        RunkkBadge(kindLabel(state.cardKind))
        Text(
            text = listOfNotNull(state.startedAt?.let(::dateLabel), state.weather).joinToString(" · "),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (state.uploaded) "달력에 저장했어요" else "보내는 중",
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
    val distance = state.distance ?: Distance.ZERO
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${RunkkFormat.km(distance)} 킬로미터"
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            "시간",
            state.elapsed?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY,
            spoken = state.elapsed?.let(RunkkFormat::durationSpoken),
            centered = true,
            valueStyle = RunkkTextStyles.DisplaySmall,
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            "평균 페이스",
            RunkkFormat.pace(state.averagePace),
            spoken = RunkkFormat.paceSpoken(state.averagePace),
            centered = true,
            valueStyle = RunkkTextStyles.DisplaySmall,
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            "평균 심박",
            state.averageHeartRate?.toString() ?: RunkkFormat.EMPTY,
            centered = true,
            valueStyle = RunkkTextStyles.DisplaySmall,
            modifier = Modifier.weight(1f)
        )
    }
    if (state.calories != null) {
        Text("소모 칼로리 약 ${state.calories}kcal", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
    RunkkCard(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        if (state.route.size >= 2) {
            RoutePreview(state.route, modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            if (state.route.size >=
                2
            ) {
                "코스"
            } else {
                "코스 기록 없음"
            },
            style = RunkkTextStyles.Micro,
            color = RunkkColors.TextSecondary
        )
    }
    if (state.heartRateZones.isNotEmpty()) {
        RunkkCard {
            Row {
                Text(
                    "심박 구간",
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "워치 · 평균 ${state.averageHeartRate ?: RunkkFormat.EMPTY}",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
            val total = state.heartRateZones.sumOf { it.second.seconds }.coerceAtLeast(1)
            RunkkSegmentBar(
                segments = state.heartRateZones.mapIndexed { index, (_, duration) ->
                    duration.seconds.toFloat() / total to
                        ZONE_COLORS[index % ZONE_COLORS.size]
                },
                modifier = Modifier.semantics {
                    contentDescription =
                        "심박 구간 분포: " + state.heartRateZones.joinToString { (zone, d) -> "$zone ${d.toMinutes()}분" }
                }
            )
            Text(
                state.heartRateZones.joinToString("  ") { (zone, d) -> "$zone ${d.toMinutes()}분" },
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

/** 경로선을 칸에 맞춰 그린다(지도 없이). 시작은 흰 점, 끝은 라임 점. */
@Composable
internal fun RoutePreview(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = "코스 경로" }) {
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

internal fun kindLabel(kind: CardKind?): String = when (kind) {
    CardKind.FREE_RUN, null -> "자유런"
    CardKind.BATTLE_RUN -> "배틀런"
    CardKind.SELF_RUN -> "나와의 싸움"
    CardKind.CELEB_RUN -> "셀럽런"
    CardKind.CREW_RUN -> "크루런"
}

private val DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

internal fun dateLabel(at: Instant): String = DATE.format(at.atZone(ZoneId.systemDefault()))

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

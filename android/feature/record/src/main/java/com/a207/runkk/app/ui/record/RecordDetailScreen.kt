package com.a207.runkk.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSegmentBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RunSummary
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 기록 상세(C2): 코스 지도, 거리·시간·페이스, 심박 구간, 칼로리·크루 기여·리워드. "카드 공유"는 카드 팝업으로. */
@Composable
fun RecordDetailRoute(recordId: String, viewModel: RecordDetailViewModel = hiltViewModel()) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RecordDetailScreen(
        state = state,
        onBack = navigator::back,
        onShare = { navigator.navigate(Routes.RecordCard(recordId)) },
        onRetry = { viewModel.load(recordId, force = true) }
    )
}

@Composable
fun RecordDetailScreen(
    state: RecordDetailUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = "기록 상세", onBack = onBack) {
            if (state.detail != null) RunkkPillButton("카드 공유", onClick = onShare, leadingIcon = RunkkIcons.Share)
        }
        val detail = state.detail
        when {
            detail != null -> DetailBody(detail)
            state.loading -> RunkkLoading(modifier = Modifier.weight(1f))
            else -> RunkkErrorState(state.error ?: "기록을 불러오지 못했어요", onRetry = onRetry)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(detail: RecordDetail) {
    val s = detail.summary
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(
            rememberScrollState()
        ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkBadge(kindLabel(s.cardKind))
            if (s.valid) {
                RunkkBadge(
                    "유효 러닝",
                    tone = RunkkBadgeTone.Lime
                )
            } else {
                RunkkBadge("집계 제외", tone = RunkkBadgeTone.Muted)
            }
            Text(
                listOfNotNull(
                    DATE.format(s.startedAt.atZone(ZoneId.systemDefault())),
                    detail.weather
                ).joinToString(" · "),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        detail.newRecords.forEach { record ->
            RunkkBanner(record, strong = true) {
                Icon(
                    RunkkIcons.Trophy,
                    contentDescription = null,
                    tint = RunkkColors.OnLime,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        RunkkCard(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RunkkShapes.ExtraLarge,
            contentPadding = PaddingValues(8.dp)
        ) {
            CourseMapView(
                points = detail.route,
                unstable = unstableIndexRanges(detail.unstableSegments, s.elapsed, detail.route.size),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            if (detail.route.size <
                2
            ) {
                Text(
                    "코스 지도는 본인이거나 공유를 켠 기록만 보여요",
                    style = RunkkTextStyles.Micro,
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkStatTile(
                "거리",
                RunkkFormat.km(s.distance),
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "시간",
                RunkkFormat.duration(s.elapsed),
                spoken = RunkkFormat.durationSpoken(s.elapsed),
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "평균 페이스",
                RunkkFormat.pace(s.averagePace),
                spoken = RunkkFormat.paceSpoken(s.averagePace),
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
        }
        if (detail.splitPaces.isNotEmpty()) SplitPaces(detail)
        if (detail.heartRateZones.isNotEmpty() || detail.averageHeartRate != null) {
            RunkkCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        RunkkIcons.Heart,
                        contentDescription = null,
                        tint = RunkkColors.Lavender,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "심박 · 워치",
                        style = RunkkTextStyles.text(14, FontWeight.Black),
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    heartSummary(detail)?.let {
                        Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                    }
                }
                val zones = detail.heartRateZones.toList()
                if (zones.isEmpty()) return@RunkkCard
                val total = zones.sumOf { it.second.seconds }.coerceAtLeast(1)
                RunkkSegmentBar(
                    zones.mapIndexed { i, (_, d) -> d.seconds.toFloat() / total to ZONE_COLORS[i % ZONE_COLORS.size] },
                    modifier = Modifier.semantics {
                        contentDescription =
                            "심박 구간 분포: " + zones.joinToString { (z, d) -> "$z ${d.toMinutes()}분" }
                    }
                )
                Text(
                    zones.joinToString("  ") { (z, d) ->
                        "$z ${d.toMinutes()}분"
                    },
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        val extras = listOfNotNull(
            detail.calories?.let { "소모 칼로리" to "약 ${it}kcal" },
            detail.crewContribution?.let { "크루 거리 기여" to "${RunkkFormat.km(it)}km" },
            detail.rewardPoints?.let { "리워드" to "+$it" }
        )
        if (extras.isNotEmpty()) {
            RunkkCard {
                extras.forEach { (label, value) ->
                    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(
                            label,
                            style = RunkkTextStyles.Body,
                            color = RunkkColors.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(value, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
                    }
                }
            }
        }
    }
}

/** 평균·최고 심박 한 줄(하이파이 HiRecordDetail "평균 [bpm] · 최고 [bpm]"). 둘 다 없으면 null. */
private fun heartSummary(detail: RecordDetail): String? {
    val average = detail.averageHeartRate ?: return detail.maxHeartRate?.let { "최고 $it bpm" }
    return listOfNotNull("평균 $average", detail.maxHeartRate?.let { "최고 $it" }).joinToString(" · ") + " bpm"
}

/** 1km 구간 페이스(하이파이 HiRecordDetail): 빠를수록 막대가 길고, 가장 빠른 구간은 라임. */
@Composable
private fun SplitPaces(detail: RecordDetail) {
    val splits = detail.splitPaces
    val fastest = detail.fastestKm ?: splits.minBy { it.secondsPerKm }
    val fastestKm = splits.indexOfFirst { it.secondsPerKm == fastest.secondsPerKm } + 1
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "구간 페이스",
                style = RunkkTextStyles.text(14, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (fastestKm > 0) {
                Text(
                    "최고 1km 기록 ${fastestKm}km · ${RunkkFormat.pace(fastest)}",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lime
                )
            }
        }
        splits.forEachIndexed { index, pace ->
            val best = index + 1 == fastestKm
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "${index + 1}킬로미터 구간 ${RunkkFormat.paceSpoken(pace)}" +
                        if (best) ", 가장 빠름" else ""
                }
            ) {
                Text(
                    "${index + 1}K",
                    style = RunkkTextStyles.display(16),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.width(32.dp)
                )
                RunkkProgressBar(
                    progress = fastest.secondsPerKm.toFloat() / pace.secondsPerKm,
                    height = 8.dp,
                    color = if (best) RunkkColors.Lime else RunkkColors.BorderHover,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    RunkkFormat.pace(pace),
                    style = RunkkTextStyles.display(16),
                    color = if (best) RunkkColors.Lime else RunkkColors.TextPrimary,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

private val DATE = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)
private val ZONE_COLORS =
    listOf(
        RunkkColors.BorderStrong,
        RunkkColors.BorderHover,
        RunkkColors.LimeDim,
        RunkkColors.Lime,
        RunkkColors.Lavender
    )

@Preview(name = "C2 기록 상세", widthDp = 390, heightDp = 1000)
@Composable
private fun RecordDetailPreview() {
    RunkkTheme(reduceMotion = true) {
        RecordDetailScreen(
            state = RecordDetailUiState(
                loading = false,
                recordId = "r",
                detail = RecordDetail(
                    summary = RunSummary(
                        "r",
                        Instant.parse("2026-10-20T10:00:00Z"),
                        Distance(5_020),
                        Duration.ofSeconds(1_668),
                        Pace(332),
                        true,
                        RunMode.FREE,
                        CardKind.FREE_RUN
                    ),
                    route = listOf(
                        GeoPoint(37.50, 127.00),
                        GeoPoint(37.506, 127.008),
                        GeoPoint(37.502, 127.016),
                        GeoPoint(37.51, 127.024)
                    ),
                    unstableSegments = emptyList(),
                    calories = 312,
                    heartRateZones = mapOf(
                        "Z1" to Duration.ofMinutes(2),
                        "Z2" to Duration.ofMinutes(8),
                        "Z3" to Duration.ofMinutes(12),
                        "Z4" to Duration.ofMinutes(5)
                    ),
                    newRecords = listOf("최장 거리 신기록 · 직전 4.80km"),
                    crewContribution = Distance(5_020),
                    rewardPoints = 50,
                    weather = "12°C 맑음"
                )
            ),
            onBack = {},
            onShare = {},
            onRetry = {}
        )
    }
}

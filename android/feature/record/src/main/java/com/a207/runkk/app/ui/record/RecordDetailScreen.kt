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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.asString
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
    // 실제 화면에서만 Kakao 지도 키를 넣는다(시험·스크린샷은 기본값 빈 문자열 → Canvas 경로선).
    CompositionLocalProvider(LocalKakaoMapKey provides BuildConfig.KAKAO_NATIVE_APP_KEY) {
        RecordDetailScreen(
            state = state,
            onBack = navigator::back,
            onShare = { navigator.navigate(Routes.RecordCard(recordId)) },
            onRetry = { viewModel.load(recordId, isForced = true) }
        )
    }
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
        RunkkTopBar(title = stringResource(R.string.record_detail_title), onBack = onBack) {
            if (state.detail != null) {
                RunkkPillButton(
                    stringResource(R.string.record_detail_share),
                    onClick = onShare,
                    leadingIcon = RunkkIcons.Share
                )
            }
        }
        val detail = state.detail
        when {
            detail != null -> DetailBody(detail)

            state.isLoading -> RunkkLoading(modifier = Modifier.weight(1f))

            else -> RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.record_detail_load_failed),
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun DetailBody(detail: RecordDetail) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(
            rememberScrollState()
        ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailBadges(detail)
        DetailCourseCard(detail)
        DetailStats(detail.summary)
        if (detail.splitPaces.isNotEmpty()) SplitPaces(detail)
        if (detail.heartRateZones.isNotEmpty() || detail.averageHeartRate != null) HeartRateCard(detail)
        // 보드 C2에는 없지만 SRS 필수 항목(신기록 FR-186, 소모 열량 FR-079, 크루 반영·리워드 FR-078)이라 끝에 둔다.
        detail.newRecords.forEach { record ->
            RunkkBanner(record, isStrong = true) {
                Icon(
                    RunkkIcons.Trophy,
                    contentDescription = null,
                    tint = RunkkColors.OnLime,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DetailExtras(detail)
    }
}

/** 맨 위 줄: 종류·유효 여부 배지와 날짜·날씨. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBadges(detail: RecordDetail) {
    val s = detail.summary
    val dateFormat = rememberDateFormatter(R.string.record_detail_date_pattern)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkBadge(kindLabel(s.cardKind))
        if (s.isValid) {
            RunkkBadge(
                stringResource(R.string.record_detail_valid),
                tone = RunkkBadgeTone.Lime
            )
        } else {
            RunkkBadge(stringResource(R.string.record_excluded), tone = RunkkBadgeTone.Muted)
        }
        Text(
            listOfNotNull(
                dateFormat.format(s.startedAt.atZone(ZoneId.systemDefault())),
                detail.weather
            ).joinToString(", "),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

/** 코스 지도 카드. 경로가 없으면 공개 범위 안내를 덧붙인다. */
@Composable
private fun DetailCourseCard(detail: RecordDetail) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = PaddingValues(8.dp)
    ) {
        CourseMapView(
            points = detail.route,
            unstable = remember(detail.unstableSegments, detail.summary.elapsed, detail.route.size) {
                unstableIndexRanges(detail.unstableSegments, detail.summary.elapsed, detail.route.size)
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

/** 거리·시간·평균 페이스 세 칸. */
@Composable
private fun DetailStats(s: RunSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.record_detail_stat_distance),
            StatTileValue(
                RunkkFormat.km(s.distance),
                unit = "km",
                spoken = stringResource(R.string.record_distance_spoken, RunkkFormat.km(s.distance))
            ),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.record_detail_stat_time),
            StatTileValue(RunkkFormat.duration(s.elapsed), spoken = RunkkFormat.durationSpoken(s.elapsed)),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.record_stat_avg_pace),
            StatTileValue(RunkkFormat.pace(s.averagePace), spoken = RunkkFormat.paceSpoken(s.averagePace)),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 심박 카드: 평균·최고 심박 요약과 구간 분포 막대. */
@Composable
private fun HeartRateCard(detail: RecordDetail) {
    val zones = detail.heartRateZones.toList()
    val zoneTexts = zones.map { (z, d) -> stringResource(R.string.record_zone_item, z, d.toMinutes()) }
    val distributionSpoken = stringResource(R.string.record_zone_spoken, zoneTexts.joinToString())
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
                stringResource(R.string.record_heart_title),
                style = RunkkTextStyles.text(14, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            heartSummary(detail)?.let {
                Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
        if (zones.isEmpty()) return@RunkkCard
        val total = zones.sumOf { it.second.seconds }.coerceAtLeast(1)
        val zoneColors = zoneColors()
        RunkkSegmentBar(
            zones.mapIndexed { i, (_, d) -> d.seconds.toFloat() / total to zoneColors[i % zoneColors.size] },
            modifier = Modifier.semantics { contentDescription = distributionSpoken }
        )
        Text(
            zoneTexts.joinToString("  "),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 칼로리·크루 기여·리워드 카드. 값이 하나도 없으면 그리지 않는다. */
@Composable
private fun DetailExtras(detail: RecordDetail) {
    val extras = listOfNotNull(
        detail.calories?.let {
            stringResource(R.string.record_extra_calories) to stringResource(R.string.record_extra_calories_value, it)
        },
        detail.crewContribution?.let {
            stringResource(R.string.record_extra_crew) to
                stringResource(R.string.record_km_value, RunkkFormat.km(it))
        },
        detail.rewardPoints?.let {
            stringResource(R.string.record_extra_reward) to stringResource(R.string.record_extra_reward_value, it)
        }
    )
    if (extras.isEmpty()) return
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

/** 평균·최고 심박 한 줄(하이파이 HiRecordDetail "평균 [bpm] · 최고 [bpm]"). 둘 다 없으면 null. */
@Composable
private fun heartSummary(detail: RecordDetail): String? {
    val average = detail.averageHeartRate
    val max = detail.maxHeartRate
    return when {
        average != null && max != null -> stringResource(R.string.record_heart_avg_max, average, max)
        average != null -> stringResource(R.string.record_heart_avg_only, average)
        max != null -> stringResource(R.string.record_heart_max_only, max)
        else -> null
    }
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
                stringResource(R.string.record_split_title),
                style = RunkkTextStyles.text(14, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (fastestKm > 0) {
                Text(
                    stringResource(R.string.record_split_best, fastestKm, RunkkFormat.pace(fastest)),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lime
                )
            }
        }
        splits.forEachIndexed { index, pace ->
            SplitPaceRow(index, pace, fastest, isBest = index + 1 == fastestKm)
        }
    }
}

/** 구간 한 줄: 번호, 페이스 막대, 페이스. */
@Composable
private fun SplitPaceRow(index: Int, pace: Pace, fastest: Pace, isBest: Boolean) {
    val spoken = stringResource(
        if (isBest) R.string.record_split_spoken_best else R.string.record_split_spoken,
        index + 1,
        RunkkFormat.paceSpoken(pace)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(
            "${index + 1}km",
            style = RunkkTextStyles.display(16),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.width(48.dp)
        )
        RunkkProgressBar(
            progress = fastest.secondsPerKm.toFloat() / pace.secondsPerKm,
            height = 8.dp,
            color = if (isBest) RunkkColors.Lime else RunkkColors.BorderHover,
            modifier = Modifier.weight(1f)
        )
        Text(
            RunkkFormat.pace(pace),
            style = RunkkTextStyles.display(16),
            color = if (isBest) RunkkColors.Lime else RunkkColors.TextPrimary,
            modifier = Modifier.width(40.dp)
        )
    }
}

/** 심박 구간 막대 색(낮은 구간부터). 지금 테마 벌에서 고른다. */
@Composable
private fun zoneColors(): List<Color> = listOf(
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
                isLoading = false,
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
                    newRecords = listOf("최장 거리 신기록, 직전 4.80km"),
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

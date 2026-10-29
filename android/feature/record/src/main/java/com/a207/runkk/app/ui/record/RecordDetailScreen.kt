package com.a207.runkk.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
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

/**
 * 기록 상세(G-9, D-280): 종류, 날짜·날씨, 코스 지도, 거리·운동 시간·평균 페이스, 정지 구간, 구간 페이스와 가장 빠른 1km, 소모 열량,
 * 심박. 유효·집계 제외는 보이지 않는다. 삭제할 수 있는 기록(집계 제외)에만 아래에 "기록 삭제"를 둔다. "카드 공유"는 CARD_SHARE로 숨긴다.
 */
@Composable
fun RecordDetailRoute(recordId: String, viewModel: RecordDetailViewModel = hiltViewModel()) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        // 지워졌거나 이미 없으면 목록으로 돌아간다. 목록은 다시 보일 때 새로 읽는다.
        viewModel.events.collect { if (it == RecordDetailEvent.DELETED) navigator.back() }
    }
    // 실제 화면에서만 Kakao 지도 키를 넣는다(시험·스크린샷은 기본값 빈 문자열 → Canvas 경로선).
    CompositionLocalProvider(LocalKakaoMapKey provides BuildConfig.KAKAO_NATIVE_APP_KEY) {
        RecordDetailScreen(
            state = state,
            onBack = navigator::back,
            onShare = { navigator.navigate(Routes.RecordCard(recordId)) },
            onRetry = { viewModel.load(recordId, isForced = true) },
            deleteActions = remember(viewModel) {
                RecordDeleteActions(
                    onDelete = viewModel::onDeleteTapped,
                    onConfirm = viewModel::onDeleteConfirmed,
                    onDismiss = viewModel::onDeleteDismissed,
                    onMessageShown = viewModel::onMessageShown
                )
            }
        )
    }
}

@Composable
fun RecordDetailScreen(
    state: RecordDetailUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    deleteActions: RecordDeleteActions = RecordDeleteActions()
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            deleteActions.onMessageShown()
        }
    }
    val canShare = LocalReleaseScope.current.isOpen(UnreleasedFeature.CARD_SHARE)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.record_detail_title), onBack = onBack) {
                if (state.detail != null && canShare) {
                    RunkkPillButton(
                        stringResource(R.string.record_detail_share),
                        onClick = onShare,
                        leadingIcon = RunkkIcons.Share
                    )
                }
            }
            val detail = state.detail
            when {
                detail != null -> DetailBody(detail, isDeleting = state.isDeleting, onDelete = deleteActions.onDelete)

                state.isLoading -> RunkkLoading(modifier = Modifier.weight(1f))

                else -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.record_detail_load_failed),
                    onRetry = onRetry
                )
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
    if (state.isConfirmingDelete) {
        RunkkConfirmDialog(
            title = stringResource(R.string.record_delete_title),
            message = stringResource(R.string.record_delete_message),
            confirmLabel = stringResource(R.string.record_delete_confirm),
            onConfirm = deleteActions.onConfirm,
            onDismiss = deleteActions.onDismiss,
            dismissLabel = stringResource(R.string.record_delete_cancel),
            isDestructive = true
        )
    }
}

@Composable
private fun DetailBody(detail: RecordDetail, isDeleting: Boolean, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(
            rememberScrollState()
        ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailHeader(detail)
        DetailCourseCard(detail)
        DetailStats(detail.summary)
        if (detail.pauseCount > 0) PausesCard(detail)
        if (detail.splitPaces.isNotEmpty()) SplitPaces(detail)
        detail.calories?.let { CaloriesCard(it) }
        if (detail.heartRateZones.isNotEmpty() || detail.averageHeartRate != null) HeartRateCard(detail)
        if (detail.isDeletable) {
            RunkkSecondaryButton(
                stringResource(R.string.record_detail_delete),
                onClick = onDelete,
                isEnabled = !isDeleting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 맨 위 줄: 종류(크루가 닫힌 1차 배포에서는 크루런 배지를 뺀다, AN8-12), 본인 기록이면 공개 범위 칩(서버 recordVisibility, 없으면 칩 없음),
 * "[날짜 시각], 12°C 맑음".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(detail: RecordDetail) {
    val s = detail.summary
    val dateFormat = rememberDateFormatter(R.string.record_detail_date_pattern)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.cardKind != CardKind.CREW_RUN || LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)) {
            RunkkBadge(kindLabel(s.cardKind))
        }
        VISIBILITY_NAMES[detail.recordVisibility]?.let { RunkkBadge(stringResource(it), tone = RunkkBadgeTone.Muted) }
        Text(
            listOfNotNull(dateFormat.format(s.startedAt.atZone(ZoneId.systemDefault())), weatherText(detail))
                .joinToString(", "),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

/** 날씨 "12°C 맑음"(서버 temperatureC·sky). 둘 중 하나라도 없으면 옛 서버의 weather 문구, 그것도 없으면 null. */
@Composable
private fun weatherText(detail: RecordDetail): String? {
    val temperature = detail.temperatureC
    val sky = SKY_NAMES[detail.sky]?.let { stringResource(it) }
    return if (temperature != null && sky != null) {
        stringResource(R.string.record_detail_weather, temperature, sky)
    } else {
        detail.weather
    }
}

/** 기록 공개 범위(서버 RecordVisibility 이름) → 칩 문구. 설정 화면의 공개 범위 선택지와 같은 말을 쓴다. */
private val VISIBILITY_NAMES = mapOf(
    "PUBLIC" to R.string.record_visibility_public,
    "CREW" to R.string.record_visibility_crew,
    "PRIVATE" to R.string.record_visibility_private
)

private val SKY_NAMES = mapOf(
    "CLEAR" to R.string.record_sky_clear,
    "CLOUDY" to R.string.record_sky_cloudy,
    "OVERCAST" to R.string.record_sky_overcast,
    "RAIN" to R.string.record_sky_rain,
    "SNOW" to R.string.record_sky_snow
)

/** "정지 구간 n회, n분". */
@Composable
private fun PausesCard(detail: RecordDetail) {
    RunkkCard {
        Text(
            stringResource(R.string.record_detail_pauses, detail.pauseCount, detail.paused.toMinutes()),
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary
        )
    }
}

/** 소모 열량 한 줄. */
@Composable
private fun CaloriesCard(calories: Int) {
    RunkkCard {
        Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.record_extra_calories),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.record_extra_calories_value, calories),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** 코스 지도 카드. GPS가 불안정했던 구간은 경로 위에 따로 표시한다. */
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
    // 가장 빠른 구간 번호는 구간 페이스 최솟값 위치로 앱이 정한다(같으면 앞 구간, D-280).
    val fastestKm = fastestSplit(splits) ?: return
    val fastest = splits[fastestKm - 1]
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.record_split_title),
                style = RunkkTextStyles.text(14, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.record_split_best, fastestKm, RunkkFormat.pace(fastest)),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextPrimary
            )
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

@Preview(name = "G-9 기록 상세", widthDp = 390, heightDp = 1000)
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
                    weather = null,
                    splitPaces = listOf(Pace(341), Pace(335), Pace(318), Pace(330), Pace(336)),
                    temperatureC = 12,
                    sky = "CLEAR",
                    pauseCount = 2,
                    paused = Duration.ofMinutes(3)
                )
            ),
            onBack = {},
            onShare = {},
            onRetry = {}
        )
    }
}

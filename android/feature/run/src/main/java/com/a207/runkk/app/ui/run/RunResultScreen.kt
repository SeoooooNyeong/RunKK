package com.a207.runkk.app.ui.run

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AchievementCard
import com.a207.runkk.app.core.designsystem.HeartZoneSection
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ResultBottomBar
import com.a207.runkk.app.core.designsystem.ResultDistanceHero
import com.a207.runkk.app.core.designsystem.ResultStage
import com.a207.runkk.app.core.designsystem.ResultStageDefaultAvatar
import com.a207.runkk.app.core.designsystem.RunMetricCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.SplitPaceSection
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.resultSheetTop
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MyAvatar
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 러닝 결과(B-6, FR-289). 「기록 카드 만들기」는 ui.record의 카드 팝업으로, 「홈으로」는 홈으로 간다. */
@Composable
fun RunResultRoute(recordId: String, isJustFinished: Boolean = false, viewModel: RunResultViewModel = hiltViewModel()) {
    // 평가 화면은 측정 직후 진입에서 한 번만(U8-6). 연 적이 있는지는 화면 상태로 저장해 백 스택이 복원돼도 다시 열지 않는다.
    var isEvaluationOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(recordId) { viewModel.load(recordId, opensEvaluation = isJustFinished && !isEvaluationOpened) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RunResultEvent.NavigateEvaluation -> event.evaluation.let {
                    isEvaluationOpened = true
                    navigator.navigate(Routes.Evaluation(it.tier, it.validRuns, it.runimalType, it.previousTier))
                }
            }
        }
    }
    RunResultScreen(
        state = state,
        onCard = { navigator.navigate(Routes.RecordCard(recordId)) },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(recordId, isForced = true) }
    )
}

/**
 * 러닝 결과 상용안(FR-289, [임시:FR-성-33], 피그마 62431:2053): 위쪽 무대 배경 위에 선 내 전신 아바타(발끝 266, 높이 180, [ResultStage]),
 * 그 위에 겹친 가운데 제목 「러닝 결과」, 300부터 올라오는 윗모서리 둥근 시트에 거리 히어로(종류 칩·날짜, 거리 60), 지표 카드(시간·평균
 * 페이스·평균 심박, 심박이 없으면 두 칸) 안에 구간 페이스(가장 빠른 구간 보라)·심박 구간(4단계 누적 막대), 성과 카드. 하단 고정 바
 * (「홈으로」, 「기록 카드 만들기」). 시트는 스크롤하며 무대를 덮고 하단 바는 고정이다. 티어 배지는 없다.
 * 기록 카드는 서버에 올라간 뒤에 열 수 있다(카드 값은 서버가 준다).
 */
@Composable
fun RunResultScreen(
    state: RunResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distance = state.distance
    val elapsed = state.elapsed
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (distance != null && elapsed != null) {
            ResultStage(resultBackgroundKey(state.myAvatar, isAvatarOpen)) { slot ->
                AvatarStillSlot(AvatarStillSubject.MINE, slot) { ResultStageDefaultAvatar(Modifier.fillMaxSize()) }
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val viewport = maxHeight
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when {
                        distance != null && elapsed != null -> {
                            val sheetTop = resultSheetTop()
                            Spacer(Modifier.height(sheetTop))
                            ResultSheet(minHeight = viewport - sheetTop) { ResultBody(state, distance, elapsed) }
                        }

                        state.isLoading -> {
                            Spacer(Modifier.height(TITLE_HEIGHT))
                            RunkkLoading(height = 240.dp)
                        }

                        else -> {
                            Spacer(Modifier.height(TITLE_HEIGHT + 4.dp))
                            RunkkErrorState(
                                state.error?.asString() ?: stringResource(R.string.run_result_not_found),
                                onRetry = onRetry,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.run_result_title),
                    style = RunkkTextStyles.text(19, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TITLE_HEIGHT)
                        .wrapContentHeight()
                        .semantics { heading() }
                )
            }
            ResultBottomBar(
                primary = stringResource(R.string.run_result_home),
                onPrimary = onHome,
                secondary = stringResource(R.string.run_result_card),
                onSecondary = onCard,
                isSecondaryEnabled = state.isUploaded,
                modifier = Modifier.testTag(RESULT_BOTTOM_BAR_TAG)
            )
        }
    }
}

/**
 * 결과 내용 시트([임시:D-성-284]): 화면 표면색, 윗모서리 28. 내용이 짧아도 화면 아래까지 채워([minHeight]) 무대 그림 바닥이 비치지 않는다.
 */
@Composable
private fun ResultSheet(minHeight: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.coerceAtLeast(0.dp))
            .clip(RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER))
            .background(RunkkColors.Surface)
            .testTag(RESULT_SHEET_TAG)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** 무대 배경 키([임시:D-성-283]): 아바타 꾸미기가 열렸으면 내 배경 키, 아니면 기본 무대(크루런 결과·홈과 같은 규칙). */
internal fun resultBackgroundKey(myAvatar: MyAvatar?, isAvatarOpen: Boolean): String? =
    if (isAvatarOpen) myAvatar?.backgroundKey else null

/** 히어로(아바타는 위쪽 무대에 선다), 지표 카드(구간 페이스·심박 구간 포함), 성과 카드. */
@Composable
private fun ResultBody(state: RunResultUiState, distance: Distance, elapsed: Duration) {
    ResultDistanceHero(
        distance = distance,
        dateLine = state.startedAt?.let {
            dateLabel(it, stringResource(R.string.run_result_hero_date_pattern))
        }.orEmpty(),
        kindLabel = stringResource(kindLabelRes(state.cardKind ?: CardKind.FREE_RUN)),
        isAvatarShown = false
    )
    RunMetricCard(
        elapsed = elapsed,
        paused = state.pausedTotal,
        averagePace = state.averagePace,
        averageHeartRate = state.averageHeartRate
    ) {
        SplitPaceSection(state.splitPaces)
        HeartZoneSection(state.heartRateZones, state.heartRateSource, state.hasBirthYear)
    }
    AchievementCard(state.achievements)
}

/** 카드 종류 이름(자유런·배틀런·셀프런·셀럽런·크루런). */
@StringRes
internal fun kindLabelRes(kind: CardKind): Int = when (kind) {
    CardKind.FREE_RUN -> R.string.run_kind_free
    CardKind.BATTLE_RUN -> R.string.run_kind_battle
    CardKind.SELF_RUN -> R.string.run_kind_self
    CardKind.CELEB_RUN -> R.string.run_kind_celeb
    CardKind.CREW_RUN -> R.string.run_kind_crew
}

/** [pattern](예: "M월 d일 (E) a h:mm")으로 시작 시각을 쓴다. */
internal fun dateLabel(at: Instant, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(at.atZone(ZoneId.systemDefault()))

/** 결과 하단 고정 바의 시험 표지. */
internal const val RESULT_BOTTOM_BAR_TAG = "run-result-bottom-bar"

/** 결과 내용 시트의 시험 표지. */
internal const val RESULT_SHEET_TAG = "run-result-sheet"

/** 무대 위에 겹치는 제목 줄 높이. */
private val TITLE_HEIGHT = 57.dp
private val SHEET_CORNER = 28.dp

@Preview(name = "B-6 러닝 결과", widthDp = 390, heightDp = 844)
@Composable
private fun RunResultPreview() {
    RunkkTheme(reduceMotion = true) {
        RunResultScreen(
            RunResultUiState(
                isLoading = false,
                recordId = "rec-1",
                cardKind = CardKind.FREE_RUN,
                startedAt = Instant.parse("2026-10-15T22:12:00Z"),
                distance = Distance(5_210),
                elapsed = Duration.ofSeconds(1_668),
                averagePace = Pace(332),
                averageHeartRate = 152,
                splitPaces = listOf(Pace(380), Pace(368), Pace(362), Pace(371), Pace(341)),
                heartRateZones = mapOf("Z2" to Duration.ofMinutes(8), "Z3" to Duration.ofMinutes(14)),
                heartRateSource = RunResultUiState.HEART_SOURCE_WATCH,
                pausedTotal = Duration.ofSeconds(185),
                isUploaded = true
            ),
            {},
            {},
            {}
        )
    }
}

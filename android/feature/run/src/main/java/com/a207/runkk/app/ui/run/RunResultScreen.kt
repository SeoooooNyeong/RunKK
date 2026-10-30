package com.a207.runkk.app.ui.run

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunResultDetail
import com.a207.runkk.app.core.designsystem.RunResultDetailState
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 러닝 결과(B-6). "기록 카드"는 ui.record의 카드 팝업으로, "홈으로"는 홈으로 간다. */
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
                    navigator.navigate(Routes.Evaluation(it.tier, it.validRuns, it.runimalType))
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
 * 러닝 결과(D-282, 피그마 B-6): 제목 "러닝 결과", 결과 상세 한 장(core.designsystem RunResultDetail, 대결 결과 2쪽과 같다),
 * 버튼 "기록 카드"·"홈으로", 결과가 있으면 피그마 배경 그림(D73). 자유런은 상세 한 장이고 옆으로 넘기는 결과 쪽은 대결만 있다(D-283).
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
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (state.toDetail() != null) AvatarBackdrop(backgroundKey = null, modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.run_result_title),
                style = RunkkTextStyles.TopBarTitle,
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp).semantics { heading() }
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val detail = state.toDetail()
                when {
                    detail != null -> RunResultDetail(detail, modifier = Modifier.padding(vertical = 8.dp))

                    state.isLoading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }

                    else -> RunkkErrorState(
                        state.error?.asString() ?: stringResource(R.string.run_result_not_found),
                        onRetry = onRetry
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RunkkSecondaryButton(
                    stringResource(R.string.run_result_card),
                    onClick = onCard,
                    isEnabled = state.isUploaded,
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
}

/** 화면 값 → 결과 상세 한 장. 거리·시간이 아직 없으면 null(읽는 중·오류). 자유런 완료는 기쁜 표정이다(D-254). */
@Composable
private fun RunResultUiState.toDetail(): RunResultDetailState? {
    val distance = distance ?: return null
    val elapsed = elapsed ?: return null
    return RunResultDetailState(
        distance = distance,
        dateLine = dateLine(startedAt, cardKind),
        elapsed = elapsed,
        rest = pausedTotal,
        averagePace = averagePace,
        averageHeartRate = averageHeartRate,
        splitPaces = splitPaces,
        heartRateZones = heartRateZones,
        newRecords = newRecords,
        rewardPoints = rewardPoints,
        expression = AvatarExpression.JOY
    )
}

/** "10.16 목 · 오전 7:12 · 자유런". 시작 시각을 모르면 종류만. */
@Composable
private fun dateLine(startedAt: Instant?, kind: CardKind?): String {
    val kindLabel = stringResource(kindLabelRes(kind ?: CardKind.FREE_RUN))
    val date = startedAt?.let { dateLabel(it, stringResource(R.string.run_result_date_pattern)) }
    return listOfNotNull(date, kindLabel).joinToString(DATE_SEPARATOR)
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

/** [pattern](예: "M.dd E · a h:mm")으로 시작 시각을 쓴다. */
internal fun dateLabel(at: Instant, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(at.atZone(ZoneId.systemDefault()))

private const val DATE_SEPARATOR = " · "

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
                newRecords = listOf("최장 거리 신기록 · 직전 4.80km"),
                heartRateZones = listOf("Z2" to Duration.ofMinutes(8), "Z3" to Duration.ofMinutes(14)),
                rewardPoints = 52,
                pausedTotal = Duration.ofSeconds(185),
                isUploaded = true
            ),
            {},
            {},
            {}
        )
    }
}

package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunResultDetail
import com.a207.runkk.app.core.designsystem.RunResultDetailState
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPageDots
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.sceneAvatarFilter
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.record.RecordDetail
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 대결 결과(C-7·C-8, D-6·D-7, E-6·E-7, D-283). "기록 카드"는 결과의 본인 기록 ID로 러닝 카드를 열고, 기록 ID가 아직 없으면(업로드 대기)
 * "기록 목록"으로 간다. "홈으로"는 홈으로 간다. 대결을 끝낸 직후([isJustFinished]) 연 결과에 티어 평가가 실리면 [onEvaluation]을
 * 한 번 부른다(D-284, U8-6). 연 적이 있는지는 화면 상태로 저장해 프로세스가 다시 떠 백 스택이 복원돼도 다시 열지 않는다.
 */
@Composable
fun BattleResultRoute(
    battleId: Long,
    isJustFinished: Boolean = false,
    viewModel: BattleResultViewModel = hiltViewModel(),
    onEvaluation: (BattleResult.Evaluation) -> Unit = {}
) {
    var isEvaluationOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(battleId) {
        viewModel.load(
            battleId,
            opensEvaluation = isJustFinished && !isEvaluationOpened,
            isJustFinished = isJustFinished
        )
    }
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BattleResultEvent.NavigateEvaluation -> {
                    isEvaluationOpened = true
                    onEvaluation(event.evaluation)
                }

                // 막 끝난 배치고사 판(D-501): 뒤 스택에 대결 결과를 남기지 않게 바꿔 연다.
                is BattleResultEvent.OpenPlacementResult -> navigator.replace(Routes.PlacementResult(event.battleId))
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BattleResultScreen(
        state = state,
        onCard = {
            val recordId = state.result?.recordId
            if (recordId !=
                null
            ) {
                navigator.navigate(Routes.RecordCard(recordId))
            } else {
                navigator.navigate(Routes.RECORDS)
            }
        },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(battleId, isForced = true) }
    )
}

/**
 * 결과 쪽(1)과 상세 쪽(2)을 옆으로 넘긴다. 판정 전·보류·오류는 결과 쪽 자리에 그 상태를 보인다(배틀 QA 5번, FR-108).
 * 배경 그림은 결과 쪽에만 있고 상세 쪽으로 넘기는 만큼 흐려져 상세 쪽은 화면 전체가 단색 바탕이다(R2-17, D90).
 */
@Composable
fun BattleResultScreen(
    state: BattleResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { PAGES }
) {
    val result = state.result
    val isDecided = result != null && !result.isPending
    // 배경은 홈과 같다: 아바타 꾸미기가 열렸으면 내 배경 키, 아니면 기본 배경.
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (isDecided) {
            AvatarBackdrop(
                backgroundKey = if (isAvatarOpen) state.myAvatar?.backgroundKey else null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = verdictPageVisibility(pagerState) }
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                stringResource(resultTitleRes(state.kind)),
                style = RunkkTextStyles.TopBarTitle,
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).semantics { heading() }
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    result == null && state.isLoading -> RunkkLoading()

                    result == null -> RunkkErrorState(
                        state.error?.asString() ?: stringResource(R.string.battle_result_load_failed),
                        onRetry = onRetry
                    )

                    result.isPending -> PendingBody(state.isAwaitingVerdict, onRetry)

                    else -> ResultPager(state, result, pagerState)
                }
            }
            BottomActions(result?.recordId != null, onCard, onHome)
        }
    }
}

@Composable
private fun ResultPager(state: BattleResultUiState, result: BattleResult, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                VerdictPage(state, result, onDetail = { scope.launch { pagerState.animateScrollToPage(1) } })
            } else {
                DetailPage(state, result, onBack = { scope.launch { pagerState.animateScrollToPage(0) } })
            }
        }
        PageDots(pagerState.currentPage)
    }
}

/**
 * 결과 쪽: 큰 제목(승리!·패배·무승부, 셀프런 승리는 신기록!), 내 아바타(승리·신기록·무승부 JOY, 패배 SAD)와 상대 그림, 시간 차 문구,
 * 셀프런만 비교표 2줄, "밀어서 상세 결과 보기 ›".
 */
@Composable
private fun VerdictPage(state: BattleResultUiState, result: BattleResult, onDetail: () -> Unit) {
    val kind = state.kind
    val verdict = state.verdict
    val message = verdictMessage(kind, verdict, result).asString()
    val headline =
        verdict?.let { stringResource(headlineRes(kind, it)) } ?: stringResource(R.string.battle_result_verdict_unknown)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            headline,
            style = RunkkTextStyles.display(44),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.padding(top = 48.dp).semantics {
                contentDescription = "$headline. $message"
                liveRegion = LiveRegionMode.Polite
            }
        )
        Figures(kind, verdict, AvatarCharacter.of(result.opponentGender), result.celebrityFigure, state.myAvatar)
        Text(
            message,
            style = RunkkTextStyles.text(17, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {}
        )
        if (kind == OpponentKind.SELF && result.rows.isNotEmpty()) ComparisonCard(result)
        Text(
            stringResource(R.string.battle_result_swipe_detail),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.clickable(role = Role.Button, onClick = onDetail).padding(8.dp)
        )
    }
}

/**
 * 내 아바타와 상대 그림(D-254): 배틀런 상대는 상대 성별([rival])의 상대 그림, 과거의 나는 반투명 기본 아바타, 셀럽은 결과 응답의
 * 셀럽 그림 키([celebrity])로 고른 전용 캐릭터(없으면 공통 흐린 그림).
 */
@Composable
private fun Figures(
    kind: OpponentKind,
    verdict: Verdict?,
    rival: AvatarCharacter,
    celebrity: String?,
    myAvatar: MyAvatar?
) {
    val expression = resultExpression(verdict)
    val opponent = when (kind) {
        OpponentKind.SELF -> Figure.PAST_SELF
        OpponentKind.CELEBRITY -> Figure.CELEBRITY
        else -> Figure.RIVAL
    }
    Box(modifier = Modifier.size(width = 260.dp, height = 220.dp).clearAndSetSemantics {}) {
        Box(Modifier.align(Alignment.CenterStart)) {
            OpponentFigure(
                Figure.AVATAR,
                size = 180.dp,
                expression = expression,
                colorFilter = sceneAvatarFilter(),
                myAvatar = myAvatar
            )
        }
        Box(Modifier.align(Alignment.CenterEnd).offset(y = 12.dp)) {
            OpponentFigure(
                opponent,
                size = 110.dp,
                colorFilter = sceneAvatarFilter(),
                character = rival,
                celebrityFigure = celebrity
            )
        }
    }
}

/** 셀프런 비교표 2줄(기록, 평균 페이스: 나 / 10월 2일의 나, D-283). 서버 rows를 그대로 쓴다. */
@Composable
private fun ComparisonCard(result: BattleResult) {
    val opponent =
        result.opponentName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.battle_running_opponent_self)
    val me = stringResource(R.string.battle_me)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ComparisonRow("", me, opponent, isHeader = true)
        result.rows.forEach { row ->
            val description = stringResource(R.string.battle_result_row_desc, row.item, row.mine, row.theirs)
            ComparisonRow(row.item, row.mine, row.theirs, isHeader = false, description = description)
        }
    }
}

@Composable
private fun ComparisonRow(item: String, mine: String, theirs: String, isHeader: Boolean, description: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            if (description != null) contentDescription = description
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            mine,
            style = if (isHeader) RunkkTextStyles.Caption else RunkkTextStyles.text(16, FontWeight.Black),
            color = if (isHeader) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            theirs,
            style = if (isHeader) RunkkTextStyles.Caption else RunkkTextStyles.Body,
            color = if (isHeader) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 상세 쪽(D-283): 러닝 결과 상세(D-282)와 같은 항목, 적립 합계 한 줄, "‹ 대결 결과로". 이번 주 전적·함께 달린 기록 링크는 없다. */
@Composable
private fun DetailPage(state: BattleResultUiState, result: BattleResult, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val detail = state.detail
        if (detail != null) {
            // 결과 상세의 아바타도 내 아바타(홈과 같은 얼굴)다.
            RunResultDetail(
                detail.toDetailState(state.verdict, result).copy(
                    faceKey = state.myAvatar?.faceKey,
                    faceLayers = state.myAvatar?.faceLayers.orEmpty()
                )
            )
        } else {
            Text(
                stringResource(R.string.battle_result_detail_waiting),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        Text(
            stringResource(R.string.battle_result_back_to_verdict),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            ).clickable(role = Role.Button, onClick = onBack).padding(8.dp)
        )
    }
}

/** 기록 상세 → 결과 상세 한 장. 리워드 줄은 서버 적립 합계(totalRewardPoints, 옛 서버면 기록 리워드)다. 표정은 [detailExpression]이다. */
@Composable
private fun RecordDetail.toDetailState(verdict: Verdict?, result: BattleResult): RunResultDetailState =
    RunResultDetailState(
        distance = summary.distance,
        dateLine = dateLine(summary.startedAt, summary.cardKind),
        elapsed = summary.elapsed,
        rest = paused?.takeIf { !it.isZero },
        averagePace = summary.averagePace,
        averageHeartRate = averageHeartRate,
        splitPaces = splitPaces,
        heartRateZones = heartRateZones.toList(),
        newRecords = newRecords,
        rewardPoints = result.totalRewardPoints ?: rewardPoints,
        expression = detailExpression(verdict)
    )

/** "10.16 목 오전 7:12, 배틀런". */
@Composable
private fun dateLine(startedAt: java.time.Instant, kind: CardKind): String {
    val pattern = stringResource(R.string.battle_result_date_pattern)
    val date = DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(startedAt.atZone(ZoneId.systemDefault()))
    val label = stringResource(
        when (kind) {
            CardKind.SELF_RUN -> R.string.battle_header_self_name
            CardKind.CELEB_RUN -> R.string.battle_header_celeb_name
            else -> R.string.battle_header_battle_name
        }
    )
    return "$date, $label"
}

/** 쪽 점 두 개(디자인 시스템 RunkkPageDots, 결과 화면은 간격 6과 강한 테두리색). */
@Composable
private fun PageDots(current: Int) {
    RunkkPageDots(
        current = current,
        count = PAGES,
        description = stringResource(R.string.battle_result_page_desc, current + 1, PAGES),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        gap = 6.dp,
        inactiveColor = RunkkColors.BorderStrong
    )
}

/** 판정을 기다리는 중(도착 직후) 또는 다시 읽기를 멈춘 뒤에도 판정이 없음(직접 다시 읽기). */
@Composable
private fun PendingBody(isAwaiting: Boolean, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        RunkkCard(modifier = Modifier.fillMaxWidth(), borderColor = RunkkColors.Lavender) {
            if (isAwaiting) {
                val waiting = stringResource(R.string.battle_result_waiting)
                Box(Modifier.fillMaxWidth().height(96.dp)) { RunkkLoading(label = waiting) }
                Text(
                    waiting,
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.battle_result_late),
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.Lavender,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                RunkkPillButton(
                    stringResource(R.string.battle_result_retry),
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/** 하단 버튼: 기록 카드(기록 ID가 없으면 기록 목록)와 홈으로. */
@Composable
private fun BottomActions(hasRecord: Boolean, onCard: () -> Unit, onHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RunkkSecondaryButton(
            stringResource(if (hasRecord) R.string.battle_result_record_card else R.string.battle_result_record_list),
            onClick = onCard,
            modifier = Modifier.weight(1f)
        )
        RunkkPrimaryButton(
            stringResource(R.string.battle_result_home),
            onClick = onHome,
            modifier = Modifier.weight(1f)
        )
    }
}

private const val PAGES = 2

/** 결과 쪽이 보이는 정도(0~1). 상세 쪽으로 넘어가면 0이다. */
private fun verdictPageVisibility(pagerState: PagerState): Float =
    (1f - (pagerState.currentPage + pagerState.currentPageOffsetFraction)).coerceIn(0f, 1f)

@Preview(name = "대결 결과", widthDp = 390, heightDp = 844)
@Composable
private fun BattleResultPreview() {
    RunkkTheme(reduceMotion = true) {
        BattleResultScreen(
            BattleResultUiState(
                isLoading = false,
                battleId = 9,
                result = BattleResult(
                    battleId = 9,
                    isPending = false,
                    verdict = "WIN",
                    rows = emptyList(),
                    rewardPoints = 0,
                    isNewRecord = false,
                    opponentName = "바람처럼",
                    recordId = "rec-9",
                    myReach = Duration.ofSeconds(1_650),
                    opponentReach = Duration.ofSeconds(1_692),
                    type = BattleHistory.Type.RANDOM,
                    totalRewardPoints = 52
                )
            ),
            {},
            {},
            {}
        )
    }
}

/** 결과 화면 표정(TC-9702): 패배만 SAD, 승리·신기록(셀프런 승리)·무승부·판정 없음은 JOY. */
internal fun resultExpression(verdict: Verdict?): AvatarExpression =
    if (verdict == Verdict.LOSE) AvatarExpression.SAD else AvatarExpression.JOY

/** 상세 장 표정(D84, 피그마 C-8·D-7): 뛰어오르기, 패배만 SAD. */
internal fun detailExpression(verdict: Verdict?): AvatarExpression =
    if (verdict == Verdict.LOSE) AvatarExpression.SAD else AvatarExpression.JUMP

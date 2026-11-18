package com.a207.runkk.app.ui.runimal

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.ResultBottomBar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.TierCelebration
import com.a207.runkk.app.core.designsystem.TierCelebrationCard
import com.a207.runkk.app.core.designsystem.TierCelebrationCardHeader
import com.a207.runkk.app.core.designsystem.TierCelebrationFrame
import com.a207.runkk.app.core.designsystem.TierCelebrationRunimalCard
import com.a207.runkk.app.core.designsystem.TierResultRow
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.runimal.TierChange

/**
 * 티어 재평가(G-11 v2, D-284). 경로 인자를 그리고, 확정 유형이 있으면 그 유형의 한 줄 설명을 유형 상세(GET /runimal/types/{type})에서
 * 읽어 Runimal 카드에 보인다(R2-15). 못 읽으면 설명 줄 없이 그린다. 「Runimal 유형 보기」는 평가 화면 자리를 유형 상세로 바꾸고(백 스택에
 * 평가 화면 하나뿐이어도 앱이 닫히지 않는다), 「확인」·뒤로는 닫는다.
 */
@Composable
fun EvaluationRoute(
    tier: String,
    validRuns: Int,
    runimalType: String?,
    previousTier: String? = null,
    typeViewModel: RunimalTypeViewModel = hiltViewModel()
) {
    val navigator = LocalRunkkNavigator.current
    val typeState by typeViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(runimalType) { runimalType?.let { typeViewModel.load(it) } }
    EvaluationScreen(
        tier = tier,
        validRuns = validRuns,
        runimalType = runimalType,
        onShowType = { type -> navigator.replace(Routes.RunimalType(type)) },
        onConfirm = navigator::back,
        runimalDescription = typeState.detail?.takeIf { it.type == runimalType }?.description,
        previousTier = previousTier
    )
}

/**
 * 티어 재평가 화면(피그마 G-11 v2): 배치고사 완료(C-16)와 같은 축하 구성(core/ui TierCelebration*). 상단 「티어 재평가」, 히어로 아래 결과
 * 문장(직전 티어와 비교해 승급·유지·강등, 직전 티어를 모르면 「○○ 티어예요」), 요약 카드(「n번째 유효 러닝」, 점수가 있으면 「최근 10회 평균
 * ○○점」, 직전 티어·이번 평가·다음 평가 행), 이번에 Runimal이 확정되면 Runimal 카드와 보조 단추 「Runimal 유형 보기」, 주 단추 「확인」.
 * [averageScore]는 최근 10회 평균 러닝 점수(지금 서버는 주지 않아 null, 숨긴다)다.
 */
@Composable
fun EvaluationScreen(
    tier: String,
    validRuns: Int,
    runimalType: String?,
    onShowType: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    runimalDescription: String? = null,
    previousTier: String? = null,
    averageScore: Int? = null
) {
    val change = TierChange.of(previousTier, tier)
    TierCelebrationFrame(
        title = stringResource(R.string.evaluation_title),
        onBack = onConfirm,
        hasBackdrop = true,
        bottomBar = {
            ResultBottomBar(
                primary = stringResource(R.string.evaluation_confirm),
                onPrimary = onConfirm,
                secondary = runimalType?.let { stringResource(R.string.evaluation_show_type) },
                onSecondary = { runimalType?.let(onShowType) }
            )
        },
        modifier = modifier
    ) {
        TierCelebration(title = headline(change, tier), subtitle = null, tier = tier)
        TierCelebrationCard {
            TierCelebrationCardHeader(
                stringResource(R.string.evaluation_valid_runs, validRuns),
                averageScore?.let { stringResource(R.string.evaluation_average_score, it) }
            )
            if (change != TierChange.UNKNOWN) {
                Divider()
                TierResultRow(stringResource(R.string.evaluation_row_previous), tierLabel(previousTier), previousTier)
            }
            Divider()
            TierResultRow(stringResource(R.string.evaluation_row_current), currentLabel(change, tier), tier)
            Divider()
            TierResultRow(
                stringResource(R.string.evaluation_row_next),
                stringResource(R.string.evaluation_valid_runs, validRuns + NEXT_EVALUATION_RUNS)
            )
        }
        runimalType?.let { TierCelebrationRunimalCard(it, runimalDescription) }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = RunkkColors.Border)

/** 티어 이름(서버 Tier 이름 → 한국어). UNRANKED와 모르는 값은 받은 그대로다. */
@Composable
private fun tierLabel(tier: String?): String =
    tier?.let { EvaluationTier.of(it)?.let { known -> stringResource(known.nameRes) } ?: it }.orEmpty()

/** 결과 문장: 승급 「○○로 올라갔어요」, 유지 「○○ 티어를 지켰어요」, 강등 「○○ 티어가 됐어요」, 모름 「○○ 티어예요」. */
@Composable
private fun headline(change: TierChange, tier: String): String {
    val name = tierLabel(tier)
    return when (change) {
        TierChange.UP -> stringResource(R.string.evaluation_headline_up, name, directionParticle(name))
        TierChange.KEEP -> stringResource(R.string.evaluation_headline_keep, name)
        TierChange.DOWN -> stringResource(R.string.evaluation_headline_down, name)
        TierChange.UNKNOWN -> stringResource(R.string.evaluation_headline_unknown, name)
    }
}

/** 「이번 평가」 값: 「골드 · 승급」(모르면 티어 이름만). */
@Composable
private fun currentLabel(change: TierChange, tier: String): String {
    val name = tierLabel(tier)
    val word = when (change) {
        TierChange.UP -> stringResource(R.string.evaluation_change_up)
        TierChange.KEEP -> stringResource(R.string.evaluation_change_keep)
        TierChange.DOWN -> stringResource(R.string.evaluation_change_down)
        TierChange.UNKNOWN -> return name
    }
    return stringResource(R.string.evaluation_current, name, word)
}

/** 방향 조사 「로」/「으로」: 받침이 없거나 ㄹ 받침이면 「로」. 한글이 아니면 「로」. */
internal fun directionParticle(word: String): String {
    val last = word.lastOrNull() ?: return "로"
    if (last !in '가'..'힣') return "로"
    val final = (last - '가') % JONGSEONG_COUNT
    return if (final == 0 || final == RIEUL) "로" else "으로"
}

/** 재평가는 유효 러닝 10회마다다. */
private const val NEXT_EVALUATION_RUNS = 10
private const val JONGSEONG_COUNT = 28
private const val RIEUL = 8

@Preview(name = "티어 재평가", widthDp = 390, heightDp = 844)
@Composable
private fun EvaluationPreview() {
    RunkkTheme(reduceMotion = true) {
        EvaluationScreen("GOLD", 20, "COMMUTER_TURTLE", onShowType = {}, onConfirm = {}, previousTier = "GOLD")
    }
}

package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/**
 * 평가 결과(G-11, D-284). 경로 인자만 그린다. "유형 보기"는 평가 화면 자리를 유형 상세로 바꾸고(백 스택에 평가 화면 하나뿐이어도
 * 앱이 닫히지 않는다), "확인"은 닫는다.
 */
@Composable
fun EvaluationRoute(tier: String, validRuns: Int, runimalType: String?) {
    val navigator = LocalRunkkNavigator.current
    EvaluationScreen(
        tier = tier,
        validRuns = validRuns,
        runimalType = runimalType,
        onShowType = { type -> navigator.replace(Routes.RunimalType(type)) },
        onConfirm = navigator::back
    )
}

/**
 * 유효 러닝 횟수, 새 티어 카드, 확정 유형이 있으면 Runimal 확정 카드. 첫 배치(유효 3회째)는 [runimalType]이 null이라 티어만 보인다.
 */
@Composable
fun EvaluationScreen(
    tier: String,
    validRuns: Int,
    runimalType: String?,
    onShowType: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(RunkkColors.Background).padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.evaluation_valid_runs, validRuns),
                    style = RunkkTextStyles.Label,
                    color = RunkkColors.TextSecondary
                )
                Text(
                    stringResource(R.string.evaluation_title),
                    style = RunkkTextStyles.Headline,
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                TierCard(tier)
                runimalType?.let { RunimalConfirmedCard(it) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            runimalType?.let { type ->
                RunkkSecondaryButton(
                    stringResource(R.string.evaluation_show_type),
                    onClick = { onShowType(type) },
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkPrimaryButton(
                stringResource(R.string.evaluation_confirm),
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 새 티어 카드: 별과 티어 이름. 서버 Tier 이름을 한국어로 바꾸고, UNRANKED와 모르는 값은 그대로 보인다. */
@Composable
private fun TierCard(tier: String) {
    val name = EvaluationTier.of(tier)?.let { stringResource(it.nameRes) } ?: tier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.BorderStrong, RunkkShapes.ExtraLarge)
            .padding(vertical = 28.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(RunkkIcons.Star, contentDescription = null, tint = RunkkColors.Lime, modifier = Modifier.size(36.dp))
        Text(name, style = RunkkTextStyles.text(28, FontWeight.Black), color = RunkkColors.Lime)
    }
}

/** Runimal 확정 카드: 유형 그림과 이름(평가 인자에 한 줄 설명이 없어 그리지 않는다). */
@Composable
private fun RunimalConfirmedCard(type: String) {
    val art = RunimalArt.of(type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.BorderStrong, RunkkShapes.ExtraLarge)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(art.drawableRes),
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(RunkkColors.Background)
                .padding(6.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.evaluation_runimal_confirmed),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Text(
                stringResource(art.nameRes),
                style = RunkkTextStyles.text(18, FontWeight.Black),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

@Preview(name = "평가 결과", widthDp = 390, heightDp = 844)
@Composable
private fun EvaluationPreview() {
    RunkkTheme(reduceMotion = true) {
        EvaluationScreen("GOLD", 20, "COMMUTER_TURTLE", onShowType = {}, onConfirm = {})
    }
}

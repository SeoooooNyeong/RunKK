package com.a207.runkk.app.ui.avatar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRunner

/** 내 러너(피그마 H-2, W8): 홈 아바타를 누르면 온다. 꾸미기·상점에서 돌아오면 다시 읽는다. */
@Composable
fun AvatarRunnerRoute(viewModel: AvatarRunnerViewModel = hiltViewModel()) {
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    AvatarRunnerScreen(
        state = state,
        actions = AvatarRunnerActions(
            onBack = navigator::back,
            onDecorate = { navigator.navigate(Routes.AVATAR) },
            onShop = { navigator.navigate(Routes.SHOP) },
            onRetry = viewModel::load,
            onUnlockClose = viewModel::onUnlockClose,
            onUnlockChange = viewModel::onUnlockChange
        )
    )
}

/** 내 러너 화면의 사용자 행동. */
data class AvatarRunnerActions(
    val onBack: () -> Unit = {},
    val onDecorate: () -> Unit = {},
    val onShop: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUnlockClose: () -> Unit = {},
    val onUnlockChange: () -> Unit = {}
)

@Composable
fun AvatarRunnerScreen(state: AvatarRunnerUiState, actions: AvatarRunnerActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(
            title = stringResource(R.string.avatar_runner_title),
            onBack = actions.onBack,
            containerColor = RunkkColors.Surface,
            hasDivider = true
        ) {
            state.avatar?.let { BalancePill(stringResource(R.string.avatar_runner_balance, formatReward(it.balance))) }
        }
        val avatar = state.avatar
        val runner = state.runner
        when {
            (avatar == null || runner == null) && state.isLoading -> RunkkLoading(Modifier.weight(1f))

            avatar == null || runner == null -> RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.avatar_runner_load_failed),
                onRetry = actions.onRetry,
                modifier = Modifier.weight(1f)
            )

            else -> RunnerBody(state, avatar, runner, actions, Modifier.weight(1f))
        }
    }
    state.unlock?.let { BackgroundUnlockDialog(it, onClose = actions.onUnlockClose, onChange = actions.onUnlockChange) }
}

@Composable
private fun RunnerBody(
    state: AvatarRunnerUiState,
    avatar: Avatar,
    runner: AvatarRunner,
    actions: AvatarRunnerActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RunnerCard(state, avatar, runner)
        BodyCard(runner)
        SkinCard(runner)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RunkkButton(
                stringResource(R.string.avatar_runner_decorate),
                onClick = actions.onDecorate,
                modifier = Modifier.weight(1f)
            )
            RunkkButton(
                stringResource(R.string.avatar_decorate_go_shop),
                onClick = actions.onShop,
                style = RunkkButtonStyle.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 아바타 카드(350x390, 모서리 24): 고른 배경 위에 아바타, 왼쪽 위 체형 칩, 오른쪽 위 피부톤 칩. */
@Composable
private fun RunnerCard(state: AvatarRunnerUiState, avatar: Avatar, runner: AvatarRunner) {
    val figure = stringResource(R.string.avatar_runner_figure)
    Box(
        Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.SurfaceHigh)
    ) {
        AvatarBackdrop(avatar.backgroundKey, Modifier.matchParentSize())
        MyRiveAvatar(
            state.look,
            Modifier
                .align(Alignment.BottomCenter)
                .size(width = FIGURE_WIDTH, height = FIGURE_HEIGHT)
                .clearAndSetSemantics {
                    contentDescription = figure
                    role = Role.Image
                }
        ) { LegacyStandingAvatar(avatar.renderKey, state.faceLayers) }
        TraitChip(
            stringResource(R.string.avatar_runner_body_chip, runner.bodyVariant + 1),
            Modifier.align(Alignment.TopStart).padding(14.dp)
        )
        TraitChip(
            stringResource(R.string.avatar_runner_skin_chip, stringResource(toneRes(runner.skinTone))),
            Modifier.align(Alignment.TopEnd).padding(14.dp)
        )
    }
}

/** 카드 위 칩(흰 바탕, divider 테두리, 알약, 높이 24, 글자 12 보조색). */
@Composable
internal fun TraitChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 24.dp)
            .clip(RunkkShapes.Pill)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** 체형 변화 카드(FR-163): 제목, "배가 볼록"–게이지–"근육 탄탄", 이번 주 횟수와 쉬는 날 안내. */
@Composable
private fun BodyCard(runner: AvatarRunner) {
    InfoCard(verticalSpacing = 8.dp) {
        Text(
            stringResource(R.string.avatar_runner_body_title),
            style = RunkkTextStyles.Label,
            color = RunkkColors.TextPrimary
        )
        BodyGauge(runner.bodyVariant)
        Text(
            stringResource(R.string.avatar_runner_body_guide, runner.recentRunCount, runner.restDaysBeforeDecline),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 체형 게이지: 3단계 손잡이 위치 [variant] / 2(0 배가 볼록, 2 근육 탄탄). 보조 기술에는 단계로 읽는다. */
@Composable
private fun BodyGauge(variant: Int) {
    val step = variant.coerceIn(0, MAX_VARIANT)
    val label = stringResource(bodyVariantLabelRes(step))
    val description = stringResource(R.string.avatar_runner_body_gauge, label, step + 1)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.avatar_body_low),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        BoxWithConstraints(Modifier.weight(1f).height(KNOB), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT)
                    .clip(RunkkShapes.Pill)
                    .background(RunkkColors.SurfaceHigh)
            )
            val travel = maxWidth - KNOB
            Box(
                Modifier
                    .offset(x = travel * (step.toFloat() / MAX_VARIANT))
                    .size(KNOB)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(RunkkColors.Surface)
            )
        }
        Text(
            stringResource(R.string.avatar_body_high),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 피부톤 카드(FR-164): "피부톤은 달리는 시간을 따라가요"와 근거("주로 오전 7시에 달려서 아침 톤이에요"). */
@Composable
private fun SkinCard(runner: AvatarRunner) {
    InfoCard(verticalSpacing = 2.dp, verticalPadding = 12.dp) {
        Text(
            stringResource(R.string.avatar_runner_skin_title),
            style = RunkkTextStyles.Label,
            color = RunkkColors.TextPrimary
        )
        val tone = stringResource(toneRes(runner.skinTone))
        val reason = runner.typicalStartHour?.let {
            stringResource(R.string.avatar_runner_skin_reason, hourText(it), tone)
        } ?: stringResource(R.string.avatar_runner_skin_reason_none)
        Text(reason, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

@Composable
private fun InfoCard(
    verticalSpacing: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
            .padding(horizontal = 18.dp, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        content()
    }
}

/** "오전 7시"·"오후 6시"(0시는 오전 12시, 12시는 오후 12시). */
@Composable
internal fun hourText(hour: Int): String = when {
    hour == 0 -> stringResource(R.string.avatar_hour_am, NOON)
    hour < NOON -> stringResource(R.string.avatar_hour_am, hour)
    hour == NOON -> stringResource(R.string.avatar_hour_pm, NOON)
    else -> stringResource(R.string.avatar_hour_pm, hour - NOON)
}

/** 피부톤 짧은 이름(새벽·아침·낮·저녁·밤). 모르는 값은 낮이다. */
@StringRes
internal fun toneRes(skinTone: String): Int = when (skinTone) {
    "DAWN" -> R.string.avatar_tone_dawn
    "MORNING" -> R.string.avatar_tone_morning
    "EVENING" -> R.string.avatar_tone_evening
    "NIGHT" -> R.string.avatar_tone_night
    else -> R.string.avatar_tone_daytime
}

/** 잔액 알약(surfaceHigh, 모서리 8, 높이 30, 글자 14 Medium). */
@Composable
internal fun BalancePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = 16.dp)
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(RunkkColors.SurfaceHigh)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = RunkkTextStyles.text(14, FontWeight.Medium), color = RunkkColors.TextPrimary)
    }
}

private const val MAX_VARIANT = 2
private const val NOON = 12
private val CARD_HEIGHT = 390.dp
private val FIGURE_WIDTH = 165.dp
private val FIGURE_HEIGHT = 330.dp
private val TRACK_HEIGHT = 12.dp
private val KNOB = 22.dp

@Preview(name = "H-2 내 러너", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarRunnerPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarRunnerScreen(
            AvatarRunnerUiState(
                isLoading = false,
                avatar = Avatar(null, "FOX", "STAGE3", "MORNING", emptyMap(), 1_240, true, emptyMap()),
                runner = AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(15_400), 51, 4)
            ),
            AvatarRunnerActions()
        )
    }
}

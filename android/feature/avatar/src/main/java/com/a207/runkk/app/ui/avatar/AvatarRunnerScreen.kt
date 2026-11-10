package com.a207.runkk.app.ui.avatar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.SelfieProgress

/** 내 러너(피그마 제안 B안 카드 무대형, W8): 홈 아바타를 누르면 온다. 꾸미기·상점·얼굴·배경에서 돌아오면 다시 읽는다. */
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
            onFace = { navigator.navigate(Routes.AVATAR_SETUP) },
            onBackground = { navigator.navigate(Routes.AVATAR_BACKGROUND) },
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
    val onFace: () -> Unit = {},
    val onBackground: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUnlockClose: () -> Unit = {},
    val onUnlockChange: () -> Unit = {}
)

/**
 * 내 러너 B안(62325:1678): 상단 바(제목, 리워드 칩), 무대 카드 358×460(모서리 28, 왼쪽 위 레벨 칩), 숫자 타일 3개, 진입 목록 2줄(얼굴,
 * 배경 n/m), 아래 꾸미기(라임)·상점(회색). 아바타와 숫자·단추만 두고 체형·피부톤 설명은 두지 않는다([임시:D-성-128]).
 * 읽는 동안에도 무대 자리(배경 + 로딩 표시)를 먼저 그린다([임시:D-성-106]).
 */
@Composable
fun AvatarRunnerScreen(state: AvatarRunnerUiState, actions: AvatarRunnerActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.avatar_runner_title), onBack = actions.onBack) {
            BalanceChip(state.avatar?.balance)
        }
        val avatar = state.avatar
        val runner = state.runner
        if ((avatar == null || runner == null) && !state.isLoading) {
            RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.avatar_runner_load_failed),
                onRetry = actions.onRetry,
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                RunnerStage(state, avatar)
                Spacer(Modifier.height(16.dp))
                RunnerTiles(runner)
                Spacer(Modifier.height(22.dp))
                RunnerLinks(state, actions)
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvatarPillButton(
                    stringResource(R.string.avatar_runner_decorate),
                    onClick = actions.onDecorate,
                    icon = R.drawable.avatar_ic_hanger,
                    modifier = Modifier.weight(1f)
                )
                AvatarPillButton(
                    stringResource(R.string.avatar_shop_title),
                    onClick = actions.onShop,
                    icon = R.drawable.avatar_ic_store,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    state.unlock?.let { BackgroundUnlockDialog(it, onClose = actions.onUnlockClose, onChange = actions.onUnlockChange) }
}

/** 무대 카드(358×460, 모서리 28): 고른 배경 위 바닥선에 선 아바타, 왼쪽 위 레벨 칩. 그림 하나로 읽는다. */
@Composable
private fun RunnerStage(state: AvatarRunnerUiState, avatar: Avatar?) {
    val figure = stringResource(R.string.avatar_runner_figure)
    AvatarLookStage(
        look = if (avatar == null) null else state.look,
        backgroundKey = avatar?.backgroundKey,
        faceKey = avatar?.renderKey,
        faceLayers = state.faceLayers,
        modifier = Modifier
            .fillMaxWidth()
            .height(STAGE_HEIGHT)
            .clip(RoundedCornerShape(STAGE_RADIUS))
            .background(RunkkColors.SurfaceHigh)
            .semantics {
                contentDescription = figure
                role = Role.Image
            }
    ) {
        state.level?.let { LevelChip(it, Modifier.align(Alignment.TopStart).padding(12.dp)) }
    }
}

/** 레벨 칩(흰 92%, 트로피 16 + 「Lv.12」 13 Medium). 읽기는 「레벨 12」. */
@Composable
private fun LevelChip(level: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.avatar_runner_level_description, level)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(RunkkColors.Surface.copy(alpha = CHIP_ALPHA))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.avatar_ic_trophy), null, Modifier.size(16.dp), tint = RunkkColors.TextPrimary)
        Text(
            stringResource(R.string.avatar_runner_level, level),
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 숫자 타일 3개(간격 8): 최근 7일 달린 횟수, 거리, 쉬어도 되는 날. 숫자 22 Bold + 단위 13 Medium. 모르면 「-」. */
@Composable
private fun RunnerTiles(runner: AvatarRunner?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberTile(
            icon = R.drawable.avatar_ic_run,
            value = runner?.recentRunCount?.toString(),
            unit = stringResource(R.string.avatar_runner_unit_runs),
            description = runner?.let { stringResource(R.string.avatar_runner_runs_description, it.recentRunCount) },
            modifier = Modifier.weight(1f)
        )
        val km = runner?.recentDistance?.let(RunkkFormat::kmShort)
        NumberTile(
            icon = R.drawable.avatar_ic_route,
            value = km,
            unit = stringResource(R.string.avatar_runner_unit_km),
            description = km?.let { stringResource(R.string.avatar_runner_distance_description, it) },
            modifier = Modifier.weight(1f)
        )
        NumberTile(
            icon = R.drawable.avatar_ic_fire,
            value = runner?.restDaysBeforeDecline?.toString(),
            unit = stringResource(R.string.avatar_runner_unit_days),
            description = runner?.let {
                stringResource(R.string.avatar_runner_rest_description, it.restDaysBeforeDecline)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberTile(
    @DrawableRes icon: Int,
    value: String?,
    unit: String,
    description: String?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(TILE_RADIUS)
    Column(
        modifier = modifier
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { description?.let { contentDescription = it } },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = RunkkColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: PLACEHOLDER,
                style = RunkkTextStyles.text(22, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
            Text(
                unit,
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/** 진입 목록(흰 카드, 모서리 16, 줄 48): 얼굴(만드는 중이면 진행 표시), 배경(연 수/전체). */
@Composable
private fun RunnerLinks(state: AvatarRunnerUiState, actions: AvatarRunnerActions) {
    val shape = RoundedCornerShape(TILE_RADIUS)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
    ) {
        LinkRow(
            icon = R.drawable.avatar_ic_face,
            label = stringResource(R.string.avatar_runner_face),
            onClick = actions.onFace,
            status = selfieStatus(state.selfie)
        ) {
            SelfieBadge(state.selfie)
        }
        HorizontalDivider(color = RunkkColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
        val count =
            state.unlockedBackgrounds?.let { unlocked ->
                state.totalBackgrounds?.let { stringResource(R.string.avatar_runner_background_count, unlocked, it) }
            }
        LinkRow(
            icon = R.drawable.avatar_ic_landscape,
            label = stringResource(R.string.avatar_tab_background),
            onClick = actions.onBackground,
            status = count?.let {
                stringResource(
                    R.string.avatar_runner_background_description,
                    state.unlockedBackgrounds ?: 0,
                    state.totalBackgrounds ?: 0
                )
            }
        ) {
            count?.let {
                Text(it, style = RunkkTextStyles.text(14, FontWeight.Medium), color = RunkkColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun LinkRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    status: String?,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { status?.let { stateDescription = it } }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = RunkkColors.TextSecondary)
        Text(
            label,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
        Icon(
            painterResource(R.drawable.avatar_ic_chevron),
            null,
            Modifier.size(20.dp),
            tint = RunkkColors.TextSecondary
        )
    }
}

/** 얼굴 줄 오른쪽: 만드는 중이면 돌아가는 고리 + 「만드는 중」, 실패면 「다시 해 볼 수 있어요」(피그마 AV-7 4 진행 배지). */
@Composable
private fun SelfieBadge(progress: SelfieProgress) {
    when (progress) {
        is SelfieProgress.Working -> Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {}
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = RunkkColors.Violet,
                trackColor = RunkkColors.SurfaceHigh,
                strokeWidth = 2.dp
            )
            Text(
                stringResource(R.string.avatar_selfie_badge_working),
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.Violet
            )
        }

        is SelfieProgress.Failed -> Text(
            stringResource(R.string.avatar_selfie_badge_failed),
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.clearAndSetSemantics {}
        )

        SelfieProgress.None, is SelfieProgress.Succeeded -> Unit
    }
}

@Composable
private fun selfieStatus(progress: SelfieProgress): String? = when (progress) {
    is SelfieProgress.Working -> stringResource(R.string.avatar_selfie_badge_working)
    is SelfieProgress.Failed -> stringResource(R.string.avatar_selfie_badge_failed)
    SelfieProgress.None, is SelfieProgress.Succeeded -> null
}

private const val PLACEHOLDER = "-"
private const val CHIP_ALPHA = 0.92f
private val STAGE_HEIGHT = 460.dp
private val STAGE_RADIUS = 28.dp
private val TILE_RADIUS = 16.dp

@Preview(name = "H-2 내 러너 B안", widthDp = 390, heightDp = 844)
@Composable
private fun AvatarRunnerPreview() {
    RunkkTheme(reduceMotion = true) {
        AvatarRunnerScreen(
            AvatarRunnerUiState(
                isLoading = false,
                avatar = Avatar(null, "FOX", "STAGE3", "MORNING", emptyMap(), 1_240, true, emptyMap()),
                runner = AvatarRunner("STAGE3", 1, "MORNING", 7, 3, Distance(12_400), 51, 4),
                level = 12,
                unlockedBackgrounds = 3,
                totalBackgrounds = 12
            ),
            AvatarRunnerActions()
        )
    }
}

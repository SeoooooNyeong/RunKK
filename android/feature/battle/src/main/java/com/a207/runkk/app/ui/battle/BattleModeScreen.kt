package com.a207.runkk.app.ui.battle

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import java.time.Duration
import java.time.LocalDate

/**
 * 배틀런 화면(C-1·C-9, D-268). 홈 배틀런 카드가 여기로 온다. 랜덤 대결은 "n km 랜덤 매칭 시작"으로 매칭 대기(BattleQuickMatch),
 * 친구 대결은 "카카오톡으로 친구 초대"로 초대 대기(FriendInviteWait)로 간다. 위치 동의는 ViewModel이, OS 위치 권한은 이 화면이 확인한다.
 */
@Composable
fun BattleModeRoute(viewModel: BattleModeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    var waiting by remember { mutableStateOf<NavKey?>(null) }
    // 권한 답과 상관없이 대기 화면으로 간다(거절은 대결 시작 때 안내한다).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        waiting?.let(navigator::navigate)
        waiting = null
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BattleModeEvent.Open -> {
                    val granted = MODE_LOCATION_PERMISSIONS.any {
                        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (granted) {
                        navigator.navigate(event.key)
                    } else {
                        waiting = event.key
                        permission.launch(MODE_LOCATION_PERMISSIONS)
                    }
                }
            }
        }
    }
    BattleModeScreen(
        state = state,
        actions = BattleModeActions(
            onSegment = viewModel::onSegmentSelected,
            onDistance = viewModel::onDistanceSelected,
            onRandomStart = viewModel::onRandomStartTapped,
            onInvite = viewModel::onInviteTapped,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

private val MODE_LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** 배틀런 화면의 사용자 행동. */
data class BattleModeActions(
    val onSegment: (BattleModeUiState.Segment) -> Unit = {},
    val onDistance: (Distance) -> Unit = {},
    val onRandomStart: () -> Unit = {},
    val onInvite: () -> Unit = {},
    val onConsentAgree: () -> Unit = {},
    val onConsentClose: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

@Composable
fun BattleModeScreen(state: BattleModeUiState, actions: BattleModeActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            actions.onMessageShown()
        }
    }
    val isFriend = state.segment == BattleModeUiState.Segment.FRIEND
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Column(
                modifier = Modifier.weight(
                    1f
                ).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.battle_mode_title),
                    style = RunkkTextStyles.TopBarTitle,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                RunkkSegmentedTabs(
                    options = listOf(
                        stringResource(R.string.battle_mode_random),
                        stringResource(R.string.battle_mode_friend)
                    ),
                    selectedIndex = state.segment.ordinal,
                    onSelect = { actions.onSegment(BattleModeUiState.Segment.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(
                        if (isFriend) R.string.battle_mode_headline_friend else R.string.battle_mode_headline_random
                    ),
                    style = RunkkTextStyles.Headline,
                    color = RunkkColors.TextPrimary
                )
                ModeVersus(
                    opponent = stringResource(
                        if (isFriend) R.string.battle_mode_friend_slot else R.string.battle_mode_random_slot
                    )
                )
                state.cards.forEach { card ->
                    DistanceCardRow(card, isSelected = card.distance == state.distance, onClick = {
                        actions.onDistance(card.distance)
                    })
                }
            }
            val distance = RunkkFormat.targetLabel(state.distance)
            RunkkPrimaryButton(
                text = if (isFriend) {
                    stringResource(R.string.battle_mode_invite)
                } else {
                    stringResource(R.string.battle_mode_random_start, distance)
                },
                onClick = if (isFriend) actions.onInvite else actions.onRandomStart,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(
            onAgree = actions.onConsentAgree,
            onClose = actions.onConsentClose
        )
    }
}

/** 나 VS 상대 자리(상대는 아직 모르므로 흐린 기본 아바타). */
@Composable
internal fun ModeVersus(opponent: String, opponentFigure: Figure = Figure.AVATAR) {
    val me = stringResource(R.string.battle_me)
    val description = stringResource(R.string.battle_versus_desc, me, opponent)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersusSlot { RunkkAvatar(null, AvatarExpression.JOY, Modifier.size(64.dp)) }
        Text(
            "VS",
            style = RunkkTextStyles.display(28),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        VersusSlot(isFaded = true) { OpponentFigure(opponentFigure, size = 64.dp) }
    }
}

@Composable
private fun VersusSlot(isFaded: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 140.dp)
            .clip(RunkkShapes.Small)
            .background(RunkkColors.LimeContainer)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Small)
            .alpha(if (isFaded) FADED else 1f),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 거리 카드: 거리 칩, "내 최고 기록"과 기록(없으면 "기록 없음"), 날짜. 고르면 테두리가 짙어진다. */
@Composable
internal fun DistanceCardRow(
    card: BattleModeUiState.DistanceCard,
    isSelected: Boolean,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
    label: String = RunkkFormat.targetLabel(card.distance)
) {
    val best = card.best?.let(RunkkFormat::duration)
    val noRecord = stringResource(R.string.battle_no_record)
    val description = stringResource(
        R.string.battle_card_desc,
        label,
        best ?: noRecord,
        card.recordedOn?.let(::monthDay).orEmpty()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) RunkkColors.TextSecondary else RunkkColors.Border,
                RunkkShapes.Large
            )
            .clickable(enabled = isEnabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (isEnabled) 1f else FADED)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = RunkkTextStyles.text(16, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            modifier = Modifier
                .clip(RunkkShapes.ExtraLarge)
                .background(RunkkColors.Background)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .width(52.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.battle_mode_best),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(best ?: noRecord, style = RunkkTextStyles.text(16, FontWeight.Black), color = RunkkColors.TextPrimary)
        }
        card.recordedOn?.let { Text(monthDay(it), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary) }
    }
}

private const val FADED = 0.4f

@Preview(name = "배틀런 화면", widthDp = 390, heightDp = 844)
@Composable
private fun BattleModePreview() {
    RunkkTheme(reduceMotion = true) {
        BattleModeScreen(
            BattleModeUiState(
                cards = listOf(
                    BattleModeUiState.DistanceCard(Distance(1_000), Duration.ofSeconds(292), LocalDate.of(2026, 9, 14)),
                    BattleModeUiState.DistanceCard(Distance(3_000), null, null),
                    BattleModeUiState.DistanceCard(
                        Distance(5_000),
                        Duration.ofSeconds(1_625),
                        LocalDate.of(2026, 10, 12)
                    ),
                    BattleModeUiState.DistanceCard(
                        Distance(10_000),
                        Duration.ofSeconds(3_511),
                        LocalDate.of(2026, 8, 3)
                    )
                )
            ),
            BattleModeActions()
        )
    }
}

package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.TierBadge
import com.a207.runkk.app.core.designsystem.TierBadgeSize
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import java.time.Duration

/** 랜덤 대결(FR-285): 배치 중이면 배치고사 진행으로 바꿔 열고, 「n km 매칭 시작」은 위치 권한을 확인한 뒤 매칭으로 간다. */
@Composable
fun RandomBattleRoute(viewModel: RandomBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val open = rememberBattleOpener()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RandomBattleEvent.Open -> open(event.key)
            }
        }
    }
    RandomBattleScreen(
        state = state,
        actions = RandomBattleActions(
            onBack = navigator::back,
            onDistance = viewModel::onDistanceSelected,
            onStart = viewModel::onStartTapped,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/**
 * 랜덤 대결 v5(FR-285, 피그마 61355:2909): 왼쪽 위 뒤로 + 「랜덤 대결」 + 오른쪽 위 본인 티어 배지(작게), 매칭 히어로, 「거리 선택」 칩,
 * 「내 n km 최고 기록」 카드(없으면 「아직 기록이 없어요」), 하단 보라 「n km 매칭 시작」. 대기·실시간 문구는 두지 않는다. 하단 탭은 숨긴다.
 */
@Composable
fun RandomBattleScreen(state: RandomBattleUiState, actions: RandomBattleActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            actions.onMessageShown()
        }
    }
    val distance = RunkkFormat.targetLabel(state.distance)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.random_battle_title), onBack = actions.onBack) {
                state.tierDisplay?.let { TierBadge(it, TierBadgeSize.SMALL) }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MatchHero()
                RunkkSectionHeader(
                    stringResource(R.string.random_battle_distance_title),
                    size = RunkkSectionHeaderSize.Card
                )
                DistanceChips(state.distance, actions.onDistance)
                BestRecordCard(distance, state.best)
            }
            VioletBottomBar(stringResource(R.string.random_battle_start, distance), onClick = actions.onStart)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(onAgree = actions.onConsentAgree, onClose = actions.onConsentClose)
    }
}

/** 「내 n km 최고 기록」과 시간(피그마 표면 r16 px16 py14, 8절 4). 기록이 없으면 「아직 기록이 없어요」(제안). */
@Composable
private fun BestRecordCard(distance: String, best: Duration?) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = RunkkShapes.Medium,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.random_battle_best, distance),
                style = RunkkTextStyles.Label,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                best?.let(RunkkFormat::duration) ?: stringResource(R.string.random_battle_no_best),
                style = RunkkTextStyles.text(14, FontWeight.Medium),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

@Preview(name = "C-1 랜덤 대결", widthDp = 390, heightDp = 844)
@Composable
private fun RandomBattlePreview() {
    RunkkTheme(reduceMotion = true) {
        RandomBattleScreen(
            RandomBattleUiState(
                bestByDistance = mapOf(Distance(5_000) to Duration.ofSeconds(1_625)),
                tierDisplay = TierDisplay.Confirmed("GOLD")
            ),
            RandomBattleActions()
        )
    }
}

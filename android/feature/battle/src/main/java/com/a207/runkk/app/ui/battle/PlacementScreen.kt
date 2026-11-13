package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocationConsentSheet
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator

/** 배치고사 진행(FR-279): 확정 티어면 랜덤 대결로 바꿔 열고, 「배치고사 n판 시작」은 위치 권한을 확인한 뒤 매칭으로 간다. */
@Composable
fun PlacementRoute(viewModel: PlacementViewModel = hiltViewModel()) {
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
                is PlacementEvent.Open -> open(event.key)
            }
        }
    }
    PlacementScreen(
        state = state,
        actions = PlacementActions(
            onBack = navigator::back,
            onDistance = viewModel::onDistanceSelected,
            onStart = viewModel::onStartTapped,
            onRetry = viewModel::onRetry,
            onConsentAgree = viewModel::onConsentAgreed,
            onConsentClose = viewModel::onConsentClosed,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/**
 * 배치고사 진행(FR-279, 피그마 61355:3061·61355:3230): 왼쪽 위 뒤로 + 「랜덤 대결」, 진행 카드(판 전, 안내 줄 숨김), 매칭 히어로,
 * 「거리 선택」 칩, 하단 보라 「배치고사 n판 시작」. 최고 기록 카드와 상단 티어 배지는 없다(8절 4). 진행을 못 읽으면 카드 자리에 다시 시도를
 * 두고 시작 버튼을 막는다. 하단 탭은 숨긴다.
 */
@Composable
fun PlacementScreen(state: PlacementUiState, actions: PlacementActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.random_battle_title), onBack = actions.onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val progress = state.progress
                when {
                    progress != null -> PlacementProgressBeforeRound(progress)

                    state.isLoading -> RunkkLoading(height = PROGRESS_CARD_HEIGHT)

                    else -> RunkkErrorState(
                        state.error?.asString() ?: stringResource(R.string.placement_load_failed),
                        onRetry = actions.onRetry
                    )
                }
                MatchHero()
                RunkkSectionHeader(
                    stringResource(R.string.random_battle_distance_title),
                    size = RunkkSectionHeaderSize.Card
                )
                DistanceChips(state.distance, actions.onDistance)
            }
            VioletBottomBar(
                stringResource(R.string.placement_start, state.round ?: 1),
                onClick = actions.onStart,
                isEnabled = state.canStart
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    if (state.isConsentSheetVisible) {
        LocationConsentSheet(onAgree = actions.onConsentAgree, onClose = actions.onConsentClose)
    }
}

/** 진행을 읽는 동안 카드 자리 높이(피그마 카드 132). */
private val PROGRESS_CARD_HEIGHT = 132.dp

@Preview(name = "P-1 배치고사 2/3", widthDp = 390, heightDp = 844)
@Composable
private fun PlacementPreview() {
    RunkkTheme(reduceMotion = true) {
        PlacementScreen(
            PlacementUiState(
                isLoading = false,
                progress = com.a207.runkk.app.domain.account.PlacementProgress(
                    com.a207.runkk.app.domain.account.PlacementStatus.IN_PROGRESS,
                    1,
                    2,
                    "GOLD",
                    "UNRANKED",
                    emptyList()
                )
            ),
            PlacementActions()
        )
    }
}

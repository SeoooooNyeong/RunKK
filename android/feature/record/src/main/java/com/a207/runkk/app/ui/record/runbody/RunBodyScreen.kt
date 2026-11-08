package com.a207.runkk.app.ui.record.runbody

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLimePillButton
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.ui.record.R

/** 런바디 경로(Routes.REPORT). 시스템 뒤로는 러닝 선택·상세면 탭 화면으로, 탭 화면이면 이전 화면으로 간다. */
@Composable
fun RunBodyRoute(viewModel: RunBodyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BackHandler(enabled = state.page != RunBodyPage.MAIN) { viewModel.onBack() }
    RunBodyScreen(
        state,
        RunBodyActions(
            onBack = { if (!viewModel.onBack()) navigator.back() },
            onTab = viewModel::onTab,
            onRetryReport = viewModel::refreshReport,
            onRetryRuns = viewModel::refreshRuns,
            onOpenPick = viewModel::onOpenPick,
            onPick = viewModel::onPick,
            onRetrySession = viewModel::onRetrySession,
            onOpenDetail = viewModel::onOpenDetail,
            onPreviousMonth = viewModel::onPreviousMonth,
            onNextMonth = viewModel::onNextMonth,
            onRetryMonth = viewModel::refreshMonth,
            onPeriod = viewModel::onPeriod,
            onRetryTrend = viewModel::refreshTrend
        )
    )
}

/** 런바디 화면(G-14·G-13·G-22). 러닝 선택(G-30)과 상세(G-20)는 같은 경로 안의 쪽이다([RunBodyPage]). */
@Composable
fun RunBodyScreen(state: RunBodyUiState, actions: RunBodyActions) {
    when (state.page) {
        RunBodyPage.PICK -> RunBodyPickPage(state, actions)
        RunBodyPage.DETAIL -> RunBodyDetailPage(state, actions)
        RunBodyPage.MAIN -> RunBodyMainPage(state, actions)
    }
}

@Composable
private fun RunBodyMainPage(state: RunBodyUiState, actions: RunBodyActions) {
    RunBodyFrame(stringResource(R.string.record_report_title), onBack = actions.onBack, onCoach = actions.onCoach) {
        RunkkSegmentRow(
            options = RunBodyTab.entries.map { stringResource(it.labelRes) },
            selectedIndex = state.tab.ordinal,
            onSelect = { actions.onTab(RunBodyTab.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        when (state.tab) {
            RunBodyTab.SUMMARY -> SummaryTab(state, actions)
            RunBodyTab.RUN -> RunTab(state, actions)
            RunBodyTab.MONTH -> MonthTab(state, actions)
        }
    }
}

/**
 * 런바디 공통 틀: 상단 바(뒤로 + 가운데 제목) + 스크롤 본문 + 오른쪽 아래 "AI 코치"(있을 때). 본문은 버튼에 가리지 않게 아래 여백을 둔다.
 */
@Composable
internal fun RunBodyFrame(
    title: String,
    onBack: () -> Unit,
    onCoach: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = title, onBack = onBack, isTitleCentered = true)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (onCoach != null) 96.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
        if (onCoach != null) {
            RunkkLimePillButton(
                stringResource(R.string.record_runbody_coach),
                onClick = onCoach,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

/** 불러오는 중이면 원, 오류면 다시 시도. 둘 다 아니면 [content]. */
@Composable
internal fun RunBodyLoadable(isLoading: Boolean, error: String?, onRetry: () -> Unit, content: @Composable () -> Unit) {
    when {
        isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp)) { RunkkLoading() }
        error != null -> RunkkErrorState(error, onRetry = onRetry)
        else -> content()
    }
}

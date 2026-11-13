package com.a207.runkk.app.ui.record.runbody

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
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
            onRetryTrend = viewModel::refreshTrend,
            onPreviousWeek = viewModel::onPreviousWeek,
            onNextWeek = viewModel::onNextWeek,
            onRetryWeek = viewModel::refreshWeek,
            onEditProfile = { navigator.navigate(Routes.PROFILE_EDIT) },
            onCoach = { navigator.navigate(Routes.COACH_CHAT) }
        )
    )
    // 프로필 편집에서 출생연도를 넣고 돌아오면 분석 상태를 다시 읽는다(MR !91 e2e 1)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
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
 * 런바디 공통 틀: 상단 바(뒤로 + 가운데 제목) + 스크롤 본문(좌우 20) + 오른쪽 아래 "AI 코치" 떠 있는 버튼(있을 때, 피그마 G-13 61502:3197).
 * 본문은 버튼에 가리지 않게 아래 여백을 둔다.
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
                    .padding(
                        start = PAGE_GUTTER,
                        end = PAGE_GUTTER,
                        top = 8.dp,
                        bottom = if (onCoach != null) COACH_CLEARANCE else 32.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
        if (onCoach != null) {
            CoachFab(onCoach, Modifier.align(Alignment.BottomEnd).padding(end = PAGE_GUTTER, bottom = PAGE_GUTTER))
        }
    }
}

/**
 * "AI 코치" 진입(피그마 M3 Extended FAB 124x56): 라임 채움, 모서리 16, 채운 말풍선 24 + 8 + 16 Medium, 안쪽 16, 그림자(Elevation 3).
 * 보조 기술은 글자("AI 코치")를 단추 이름으로 읽는다.
 */
@Composable
private fun CoachFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.height(COACH_FAB_HEIGHT),
        shape = RoundedCornerShape(16.dp),
        containerColor = RunkkColors.LimeFill,
        contentColor = RunkkColors.OnLimeFill
    ) {
        Icon(RunkkIcons.ChatBubble, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.record_runbody_coach), style = RunkkTextStyles.text(16, FontWeight.Medium))
    }
}

/** 불러오는 중이면 원, 오류면 다시 시도. 둘 다 아니면 [content]. */
@Composable
internal fun RunBodyLoadable(isLoading: Boolean, error: String?, onRetry: () -> Unit, content: @Composable () -> Unit) {
    when {
        isLoading -> RunkkLoading(height = LOADING_HEIGHT)
        error != null -> RunkkErrorState(error, onRetry = onRetry)
        else -> content()
    }
}

/** 본문 좌우 여백(피그마 20). */
private val PAGE_GUTTER = 20.dp
private val COACH_FAB_HEIGHT = 56.dp

/** 떠 있는 "AI 코치"(56) + 아래 20 + 본문과 띄울 20. */
private val COACH_CLEARANCE = 96.dp

/** 불러오는 칸 높이(위아래 48 + 원 40). */
private val LOADING_HEIGHT = 136.dp

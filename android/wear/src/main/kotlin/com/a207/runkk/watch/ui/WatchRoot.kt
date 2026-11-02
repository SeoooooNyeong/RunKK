package com.a207.runkk.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.TimeText

/**
 * 앱 전체 틀. 화면은 [WatchFlow] 상태 하나로 고른다(D-374, 상태도 2.4.25). 폰에서 시작한 러닝의 동행 표시([WatchFlow.Companion])는
 * 기존 화면(와이어프레임 K2·K4·K5)을 [companion] 상태로 그린다(D-380, W07).
 * 뒤로 가기는 거리 선택·셀럽 목록·매칭 중에서만 받는다: SwipeToDismissBox(오른쪽으로 쓸기)와 뒤로 버튼이 [WatchActions.onBack]을
 * 부른다(J-4). 카운트다운·측정·일시정지·매칭 완료·결과에서는 받지 않고, 하드웨어 뒤로 키도 소비해 앱이 닫히지 않게 한다(상태도 2.4.25,
 * 코드 리뷰 WR12-2: 닫히면 결과를 잃고 대결 화면이 끊긴다). 메인·미로그인·동행 표시의 뒤로 키는 시스템 기본 동작(앱 닫기)이다. 시각 표시는 동행 표시와 메인·목록 화면에만 둔다(피그마 측정·카운트다운에는 없다).
 */
@Composable
fun WatchRoot(
    flow: WatchFlow,
    companion: WatchUiState,
    actions: WatchActions,
    modifier: Modifier = Modifier,
    isAmbient: Boolean = false,
    timeText: @Composable () -> Unit = { TimeText() }
) {
    val canGoBack = flow is WatchFlow.Picker || flow is WatchFlow.CelebList || flow is WatchFlow.Matching
    BackHandler(enabled = flow.holdsBackKey()) { if (canGoBack) actions.onBack() }
    AppScaffold(
        modifier = modifier,
        timeText = if (flow.showsTime()) timeText else ({}),
        containerColor = WatchColors.Background
    ) {
        val dismiss = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(
            onDismissed = actions.onBack,
            state = dismiss,
            userSwipeEnabled = canGoBack,
            backgroundKey = BACKGROUND_KEY,
            contentKey = flow::class
        ) { isBackground ->
            if (!isBackground) FlowScreen(flow, companion, actions, isAmbient)
        }
    }
}

@Composable
private fun FlowScreen(flow: WatchFlow, companion: WatchUiState, actions: WatchActions, isAmbient: Boolean) {
    when (flow) {
        WatchFlow.NotLoggedIn -> WatchNotLoggedInScreen()

        is WatchFlow.Main -> WatchModeCarousel(flow, onModeSelected = actions.onModeSelected, isAmbient = isAmbient)

        is WatchFlow.Picker -> WatchDistancePicker(flow, actions.onDistanceSelected, actions.onStartPressed)

        is WatchFlow.CelebList -> WatchCelebListScreen(flow, actions.onCelebritySelected)

        is WatchFlow.Matching -> WatchMatchingScreen(flow, onCancel = actions.onCancelMatching)

        is WatchFlow.Matched -> WatchMatchedScreen(flow)

        is WatchFlow.Countdown -> WatchCountdownScreen(flow)

        is WatchFlow.Running -> WatchRunScreen(
            flow,
            onPause = actions.onPause,
            onEndTapped = actions.onEndTapped,
            onEndLongPressed = actions.onEndLongPressed,
            isAmbient = isAmbient
        )

        is WatchFlow.Paused -> WatchPausedScreen(flow, actions.onResume, actions.onEndTapped, actions.onEndLongPressed)

        is WatchFlow.Result -> WatchResultScreen(flow, onClose = actions.onResultClosed)

        WatchFlow.Companion -> CompanionScreen(companion, actions)
    }
}

/** 동행 표시(와이어프레임 K2·K4·K5): 정지 중이면 일시정지, 근접 알림 중이면 대결 근접 화면, 아니면 러닝 화면. */
@Composable
private fun CompanionScreen(state: WatchUiState, actions: WatchActions) {
    val proximity = state.proximity
    when {
        state.isPaused -> WatchPausedScreen(state = state, actions = actions)
        proximity != null -> WatchBattleScreen(state = state, proximity = proximity)
        else -> WatchRunDisplay(state = state, actions = actions)
    }
}

/** 하드웨어 뒤로 키를 앱이 다루는 화면: 메인·미로그인·동행 표시 말고 모두(앱을 닫아도 잃는 것이 없는 화면만 시스템에 맡긴다). */
private fun WatchFlow.holdsBackKey(): Boolean =
    this !is WatchFlow.Main && this !is WatchFlow.NotLoggedIn && this !is WatchFlow.Companion

/** 시각 표시를 둘 화면: 동행 표시, 메인, 목록. */
private fun WatchFlow.showsTime(): Boolean =
    this is WatchFlow.Companion || this is WatchFlow.Main || this is WatchFlow.NotLoggedIn

/** SwipeToDismissBox 뒤 화면 자리 키. */
private const val BACKGROUND_KEY = "watch-flow-background"

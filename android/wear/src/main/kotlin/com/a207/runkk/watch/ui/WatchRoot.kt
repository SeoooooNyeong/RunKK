package com.a207.runkk.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.TimeText

/**
 * 앱 전체 틀. 화면은 [WatchFlow] 상태 하나로 고른다(D-374, 상태도 2.4.25). 폰에서 시작한 러닝의 동행 표시([WatchFlow.Companion])는
 * [companion] 상태를 워치 단독 측정 화면 상태로 옮겨 같은 화면(피그마 FR∞·FR3·BR1·SR3·FP∞)으로 그리고, 조작은 폰에 요청한다(D-380, W35).
 * 뒤로 가기는 거리 선택·셀럽 목록·매칭 중에서만 받는다: SwipeToDismissBox(오른쪽으로 쓸기)와 뒤로 버튼이 [WatchActions.onBack]을
 * 부른다(J-4). 카운트다운·측정·일시정지·매칭 완료·결과에서는 받지 않고, 하드웨어 뒤로 키도 소비해 앱이 닫히지 않게 한다(상태도 2.4.25,
 * 코드 리뷰 WR12-2: 닫히면 결과를 잃고 대결 화면이 끊긴다). 메인·미로그인·동행 표시의 뒤로 키는 시스템 기본 동작(앱 닫기)이다. 시각 표시는 메인·미로그인 화면에만 둔다(피그마 측정·카운트다운에는 없다).
 * 화면이 바뀌면 피그마 DISSOLVE처럼 새 화면이 옛 화면 위로 나타난다(220ms, 매칭 중·완료가 끼면 250ms, W41). 저전력 표시·애니메이션 끔·
 * 절전 모드면 바로 바꾸고, 화면 안 모션도 [LocalMotionStill]로 멈춘다.
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
    BackHandler(enabled = flow.holdsBackKey()) { if (flow.canGoBack()) actions.onBack() }
    val still = isAmbient || rememberMotionReduced()
    CompositionLocalProvider(LocalMotionStill provides still) {
        RootScaffold(flow, companion, actions, modifier, isAmbient, still, timeText)
    }
}

@Composable
private fun RootScaffold(
    flow: WatchFlow,
    companion: WatchUiState,
    actions: WatchActions,
    modifier: Modifier,
    isAmbient: Boolean,
    still: Boolean,
    timeText: @Composable () -> Unit
) {
    AppScaffold(
        modifier = modifier,
        timeText = if (flow.showsTime()) timeText else ({}),
        containerColor = WatchColors.Background
    ) {
        val dismiss = rememberSwipeToDismissBoxState()
        // 쓸어 넘겨 뒤로 갔으면 옛 화면이 이미 밀려 나갔으니 다음 화면은 겹쳐 나타나지 않고 바로 바뀐다.
        val swiped = remember { mutableStateOf(false) }
        SwipeToDismissBox(
            onDismissed = {
                swiped.value = true
                actions.onBack()
            },
            state = dismiss,
            userSwipeEnabled = flow.canGoBack(),
            backgroundKey = BACKGROUND_KEY,
            // 화면 종류마다 키를 바꾸면 전환 틀이 새로 만들어져 겹쳐 나타나기가 없어진다. 화면 구분은 DissolvingFlow가 한다.
            contentKey = CONTENT_KEY
        ) { isBackground ->
            if (!isBackground) DissolvingFlow(flow, companion, actions, isAmbient, still, swiped)
        }
    }
}

/**
 * 화면 전환(피그마 DISSOLVE, EASE_OUT): 옛 화면은 그대로 두고 새 화면이 그 위로 나타난 뒤 옛 화면을 뗀다. 같은 화면 안에서 값만
 * 바뀌면([screenKey]가 같으면) 전환 없이 다시 그린다. 사라지는 화면은 마지막 값으로 그린다.
 */
@Composable
private fun DissolvingFlow(
    flow: WatchFlow,
    companion: WatchUiState,
    actions: WatchActions,
    isAmbient: Boolean,
    still: Boolean,
    swiped: MutableState<Boolean>
) {
    val key = screenKey(flow, companion)
    val last = remember { HashMap<Any, Pair<WatchFlow, WatchUiState>>() }
    last[key] = flow to companion
    LaunchedEffect(key) { swiped.value = false }
    AnimatedContent(
        targetState = key,
        transitionSpec = {
            if (still || swiped.value) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val spec = tween<Float>(screenFadeMillis(initialState, targetState), easing = ScreenEasing)
                fadeIn(spec) togetherWith ExitTransition.KeepUntilTransitionsFinished
            } using SizeTransform(clip = false)
        },
        label = "watch-screen"
    ) { shownKey ->
        val (shownFlow, shownCompanion) = if (shownKey == key) flow to companion else last.getValue(shownKey)
        FlowScreen(shownFlow, shownCompanion, actions, isAmbient)
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

        WatchFlow.Companion -> CompanionScreen(companion, actions, isAmbient)
    }
}

/** 동행 표시(W35): 워치 단독 측정·일시정지 화면을 그대로 쓰고 조작은 폰 요청(onCompanion…)으로 잇는다. */
@Composable
private fun CompanionScreen(state: WatchUiState, actions: WatchActions, isAmbient: Boolean) {
    when (val shown = state.toMeasureFlow()) {
        is WatchFlow.Paused -> WatchPausedScreen(
            shown,
            onResume = actions.onCompanionResumeTapped,
            onEndTapped = actions.onCompanionEndTapped,
            onEndLongPressed = actions.onCompanionEndLongPressed
        )

        is WatchFlow.Running -> WatchRunScreen(
            shown,
            onPause = actions.onCompanionPauseTapped,
            onEndTapped = actions.onCompanionEndTapped,
            onEndLongPressed = actions.onCompanionEndLongPressed,
            isAmbient = isAmbient
        )

        else -> Unit
    }
}

/** 뒤로 가기(쓸기·뒤로 키)를 받는 화면: 거리 선택, 셀럽 목록, 매칭 중. */
private fun WatchFlow.canGoBack(): Boolean =
    this is WatchFlow.Picker || this is WatchFlow.CelebList || this is WatchFlow.Matching

/** 하드웨어 뒤로 키를 앱이 다루는 화면: 메인·미로그인·동행 표시 말고 모두(앱을 닫아도 잃는 것이 없는 화면만 시스템에 맡긴다). */
private fun WatchFlow.holdsBackKey(): Boolean =
    this !is WatchFlow.Main && this !is WatchFlow.NotLoggedIn && this !is WatchFlow.Companion

/** 시각 표시를 둘 화면: 메인, 미로그인. */
private fun WatchFlow.showsTime(): Boolean = this is WatchFlow.Main || this is WatchFlow.NotLoggedIn

/** SwipeToDismissBox 뒤 화면 자리 키와 앞 화면 자리 키. */
private const val BACKGROUND_KEY = "watch-flow-background"
private const val CONTENT_KEY = "watch-flow-content"

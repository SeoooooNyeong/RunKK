package com.a207.runkk.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.LocalGuideAnchors
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 홈 화면 경로를 그래프에 등록하고(FeatureEntry 구현, 6.1.14), 맨 위가 홈일 때 첫 사용 가이드를 덮는다(D-277).
 * 설정의 「가이드 다시 보기」는 경로 값 [Routes.GuideReplay]로 온다([임시:D-성-312]): 이 경로가 맨 위에 오면 가이드를 1단계부터 다시
 * 열고 경로를 바로 뺀다(아래의 홈이 맨 위가 되고 그 위에 가이드가 보인다).
 */
class HomeFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) { HomeRoute() }
        // 다시 보기 경로는 신호일 뿐이라 화면이 없다. 빠지기 전 한두 프레임 동안 화면 바탕만 그린다.
        builder.addEntryProvider(Routes.GuideReplay::class) {
            Box(Modifier.fillMaxSize().background(RunkkColors.Background))
        }
    }

    @Composable
    override fun Overlay(top: NavKey?) {
        if (top == HOME_KEY || top == Routes.GuideReplay) FirstRunGuideRoute(top)
    }

    private companion object {
        val HOME_KEY = Routes.Key(Routes.HOME)
    }
}

/**
 * 가이드 상태를 읽어 시작 카드 또는 단계 말풍선(단계 목록은 ReleaseScope로 거름: 열린 배포 4단계, 1차 배포 3단계)을 그린다.
 * 맨 위 경로 [top]이 [Routes.GuideReplay]이면 다시 보기를 시작하고 그 경로를 뺀다. 가이드는 홈이 맨 위일 때만 그린다.
 */
@Composable
internal fun FirstRunGuideRoute(
    top: NavKey?,
    viewModel: FirstRunGuideViewModel = hiltViewModel(),
    navigator: RunkkNavigator = LocalRunkkNavigator.current
) {
    val isReplayRequested = top == Routes.GuideReplay
    LaunchedEffect(isReplayRequested) {
        if (isReplayRequested) {
            viewModel.onReplay()
            navigator.back()
        }
    }
    if (isReplayRequested) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val steps = GuideStep.visibleIn(LocalReleaseScope.current)
    when (state.phase) {
        GuidePhase.HIDDEN -> Unit

        GuidePhase.START_CARD -> FirstRunGuideStartCard(
            stepCount = steps.size,
            onStart = viewModel::onStartTour,
            onSkip = viewModel::onSkip
        )

        GuidePhase.STEPS -> {
            val step = steps.getOrNull(state.index) ?: return
            FirstRunGuideOverlay(
                step = step,
                index = state.index,
                stepCount = steps.size,
                anchor = LocalGuideAnchors.current[step],
                onNext = { viewModel.onNext(steps.size) },
                onPrevious = viewModel::onPrevious,
                onBack = viewModel::onBack
            )
        }
    }
}

package com.a207.runkk.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.LocalGuideAnchors
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 홈 화면 경로를 그래프에 등록하고(FeatureEntry 구현, 6.1.14), 맨 위가 홈일 때 첫 사용 가이드를 덮는다(D-277).
 */
class HomeFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) { HomeRoute() }
    }

    @Composable
    override fun Overlay(top: NavKey?) {
        if (top == HOME_KEY) FirstRunGuideRoute()
    }

    private companion object {
        val HOME_KEY = Routes.Key(Routes.HOME)
    }
}

/** 가이드 상태를 읽어 단계 목록(ReleaseScope로 거름, 2차 배포 운영 빌드 8단계)과 앵커로 막을 그린다. */
@Composable
internal fun FirstRunGuideRoute(viewModel: FirstRunGuideViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.isVisible) return
    val steps = GuideStep.visibleIn(LocalReleaseScope.current)
    val step = steps.getOrNull(state.index) ?: return
    FirstRunGuideOverlay(
        step = step,
        index = state.index,
        stepCount = steps.size,
        anchor = LocalGuideAnchors.current[step],
        onNext = { viewModel.onNext(steps.size) },
        onSkip = viewModel::onSkip
    )
}

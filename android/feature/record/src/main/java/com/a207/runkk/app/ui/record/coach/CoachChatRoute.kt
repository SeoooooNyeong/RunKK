package com.a207.runkk.app.ui.record.coach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

@Composable
internal fun CoachChatRoute(viewModel: CoachChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshConversations()
        onPauseOrDispose {}
    }
    CoachChatScreen(
        state,
        CoachChatActions(
            onBack = navigator::back,
            onSend = viewModel::send,
            onRetry = viewModel::retry,
            onNewConversation = viewModel::newConversation,
            onOpenConversation = viewModel::openConversation,
            onConsents = { navigator.navigate(Routes.COACH_CONSENTS) },
            onRefreshConversations = viewModel::refreshConversations,
            onReloadHistory = viewModel::reloadHistory
        )
    )
}

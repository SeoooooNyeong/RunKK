package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/**
 * 채팅 알림(Screen CREW_CHAT)으로 들어온 경로(D-150). 알림은 크루 ID를 경로로 넘기지 않으므로 내 크루(GET /crews/me)를 확인해
 * 이 항목을 빼고 그 크루의 채팅방(Routes.CrewChat, ui.chat)을 올린다. 소속이 없으면 안내만 보인다.
 */
@Composable
fun CrewChatEntryRoute(viewModel: CrewFindViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val crewId = state.mine?.crewId
    LaunchedEffect(crewId) {
        if (crewId != null) {
            navigator.back()
            navigator.navigate(Routes.CrewChat(crewId))
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RunkkTopBar(title = stringResource(R.string.crew_home_chat), onBack = navigator::back)
        Box(Modifier.fillMaxSize()) {
            when {
                state.isCheckingMine || crewId != null -> RunkkLoading()

                state.mineError != null -> RunkkErrorState(state.mineError?.asString().orEmpty(), onRetry = {
                    viewModel.loadMine()
                })

                else -> RunkkEmptyState(stringResource(R.string.crew_chat_no_crew))
            }
        }
    }
}

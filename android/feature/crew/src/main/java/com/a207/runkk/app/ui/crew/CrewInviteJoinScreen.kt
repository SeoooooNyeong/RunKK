package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/**
 * 초대 링크로 연 크루 가입 확인([임시:FR-성-38], Routes.CrewInviteJoin). 가입했거나 이미 구성원이면 이 화면을 그 크루 홈으로 바꾸고,
 * 만료 안내의 「크루 찾기」는 크루 검색으로 바꾼다(뒤로 가면 홈이다).
 */
@Composable
fun CrewInviteJoinRoute(key: Routes.CrewInviteJoin, viewModel: CrewInviteJoinViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.load(key.code) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CrewInviteJoinEvent.OpenCrewHome -> navigator.replace(Routes.CrewHome(event.crewId))
                CrewInviteJoinEvent.OpenSearch -> navigator.replace(Routes.Key(Routes.CREW_SEARCH))
            }
        }
    }
    val loadLogo = remember(viewModel) { viewModel::logo }
    CompositionLocalProvider(LocalCrewLogoLoader provides loadLogo) {
        CrewInviteJoinScreen(
            state = state,
            onBack = navigator::back,
            onJoin = viewModel::onJoin,
            onFindCrews = viewModel::onFindCrews,
            onRetry = viewModel::onRetry
        )
    }
}

/**
 * 가입 확인 화면: 정상은 크루 미리보기(크루 이름, 개설일, 인원, 소개, 태그)와 「가입하기」, 만료는 「만료된 초대예요. 크루원에게 새 초대를
 * 받아 주세요.」와 「크루 찾기」, 횟수 초과는 기존 문구다. 이미 구성원이면 그 크루 홈으로 가는 동안 불러오는 중을 보인다.
 */
@Composable
internal fun CrewInviteJoinScreen(
    state: CrewInviteJoinUiState,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onFindCrews: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.crew_invite_join_title), onBack = onBack)
        when (state) {
            CrewInviteJoinUiState.Loading, is CrewInviteJoinUiState.AlreadyMember -> RunkkLoading()

            is CrewInviteJoinUiState.Found -> FoundBody(state, onJoin)

            CrewInviteJoinUiState.Expired -> NoticeBody(stringResource(R.string.crew_invite_join_expired)) {
                RunkkButton(
                    stringResource(R.string.crew_invite_join_find),
                    onClick = onFindCrews,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CrewInviteJoinUiState.Limited -> NoticeBody(stringResource(R.string.crew_error_too_many_attempts)) {}

            is CrewInviteJoinUiState.Failed -> RunkkErrorState(state.message.asString(), onRetry = onRetry)
        }
    }
}

@Composable
private fun FoundBody(state: CrewInviteJoinUiState.Found, onJoin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.crew_invite_join_heading),
                style = RunkkTextStyles.SheetTitle,
                color = RunkkColors.TextPrimary
            )
            // 미리보기 카드는 눌러 상세로 가지 않는다(가입 확인만 한다).
            InvitePreviewCard(state.preview, canOpen = false, onOpen = {})
            state.message?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.text(13, lineHeight = 16),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        RunkkButton(
            stringResource(R.string.crew_invite_submit),
            onClick = onJoin,
            isLoading = state.isJoining,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 안내 글과 아래 단추 하나(만료, 횟수 초과). */
@Composable
private fun NoticeBody(message: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                message,
                style = RunkkTextStyles.text(15, lineHeight = 22),
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        action()
    }
}

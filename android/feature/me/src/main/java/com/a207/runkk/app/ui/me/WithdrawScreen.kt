package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkAccentOutlinedButton
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkCheckRow
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/** 회원 탈퇴(F6, FR-020·FR-021·FR-024): 지울 자료를 보이고 확인 체크를 받은 뒤 보낸다. */
@Composable
fun WithdrawRoute(viewModel: WithdrawViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) { viewModel.events.collect { navigator.restart(Routes.LOGIN) } }
    WithdrawScreen(
        state = state,
        onBack = navigator::back,
        onAcknowledgedChange = viewModel::onAcknowledgedChange,
        onWithdraw = viewModel::onWithdrawConfirmed,
        onErrorShown = viewModel::onErrorShown
    )
}

/** 확인할 내용(지워지는 것, 사라지는 것, 남는 것). */
private val NOTICES = listOf(
    R.string.me_withdraw_notice_deleted,
    R.string.me_withdraw_notice_rewards,
    R.string.me_withdraw_notice_crew
)

@Composable
fun WithdrawScreen(
    state: WithdrawUiState,
    onBack: () -> Unit,
    onAcknowledgedChange: (Boolean) -> Unit,
    onWithdraw: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.error?.asString()
    LaunchedEffect(state.error) {
        errorText?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.me_withdraw_title), onBack = onBack)
            WithdrawNotice(state, onAcknowledgedChange, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RunkkSecondaryButton(
                    text = stringResource(R.string.me_withdraw_cancel),
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                RunkkAccentOutlinedButton(
                    text = stringResource(
                        if (state.isWithdrawing) R.string.me_withdraw_submitting else R.string.me_withdraw_submit
                    ),
                    onClick = onWithdraw,
                    isEnabled = state.isAcknowledged && !state.isWithdrawing,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
}

/** 머리말, 확인할 내용, 확인 체크. */
@Composable
private fun WithdrawNotice(
    state: WithdrawUiState,
    onAcknowledgedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.me_withdraw_heading),
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        RunkkCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NOTICES.forEach { NoticeLine(stringResource(it)) }
        }
        RunkkCheckRow(
            label = stringResource(R.string.me_withdraw_acknowledge),
            isChecked = state.isAcknowledged,
            onCheckedChange = onAcknowledgedChange
        )
    }
}

/** 글머리 한 줄(라벤더 점 + 글). */
@Composable
private fun NoticeLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(RunkkColors.Lavender))
        Text(text, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "F6 회원 탈퇴", widthDp = 390, heightDp = 844)
@Composable
private fun WithdrawPreview() {
    RunkkTheme(reduceMotion = true) { WithdrawScreen(WithdrawUiState(isAcknowledged = true), {}, {}, {}, {}) }
}

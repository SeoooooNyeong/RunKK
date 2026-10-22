package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/** 회원 탈퇴(FR-020·FR-021·FR-024): 지울 자료를 보이고 두 번 확인한다. */
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

private val DELETED = listOf(
    R.string.me_withdraw_deleted_profile,
    R.string.me_withdraw_deleted_records,
    R.string.me_withdraw_deleted_social,
    R.string.me_withdraw_deleted_pending
)

private val KEPT = listOf(R.string.me_withdraw_kept_item)

@Composable
fun WithdrawScreen(
    state: WithdrawUiState,
    onBack: () -> Unit,
    onAcknowledgedChange: (Boolean) -> Unit,
    onWithdraw: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirm by remember { mutableStateOf(false) }
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
            RunkkAccentOutlinedButton(
                text = stringResource(
                    if (state.isWithdrawing) R.string.me_withdraw_submitting else R.string.me_withdraw_submit
                ),
                onClick = { confirm = true },
                isEnabled = state.isAcknowledged && !state.isWithdrawing,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
    if (confirm) {
        RunkkConfirmDialog(
            title = stringResource(R.string.me_withdraw_confirm_title),
            message = stringResource(R.string.me_withdraw_confirm_message),
            confirmLabel = stringResource(R.string.me_withdraw_confirm_button),
            onConfirm = {
                confirm = false
                onWithdraw()
            },
            onDismiss = { confirm = false },
            isDestructive = true
        )
    }
}

/** 제목과 글머리 항목을 담은 안내 카드. */
@Composable
private fun NoticeCard(@StringRes title: Int, items: List<Int>, titleColor: Color, itemColor: Color) {
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = RunkkTextStyles.BodyStrong, color = titleColor)
        items.forEach {
            Text(
                stringResource(R.string.me_withdraw_bullet, stringResource(it)),
                style = RunkkTextStyles.Body,
                color = itemColor
            )
        }
    }
}

/** 지워지는 것·남는 것 안내와 확인 체크. */
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
            modifier = Modifier.semantics {
                heading()
            }
        )
        NoticeCard(
            R.string.me_withdraw_deleted_title,
            DELETED,
            titleColor = RunkkColors.Lavender,
            itemColor = RunkkColors.TextPrimary
        )
        NoticeCard(
            R.string.me_withdraw_kept_title,
            KEPT,
            titleColor = RunkkColors.TextSecondary,
            itemColor = RunkkColors.TextSecondary
        )
        Text(
            stringResource(R.string.me_withdraw_blocked_notice),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        RunkkCheckRow(
            label = stringResource(R.string.me_withdraw_acknowledge),
            isChecked = state.isAcknowledged,
            onCheckedChange = onAcknowledgedChange
        )
    }
}

@Preview(name = "회원 탈퇴", widthDp = 390, heightDp = 844)
@Composable
private fun WithdrawPreview() {
    RunkkTheme(reduceMotion = true) { WithdrawScreen(WithdrawUiState(isAcknowledged = true), {}, {}, {}, {}) }
}

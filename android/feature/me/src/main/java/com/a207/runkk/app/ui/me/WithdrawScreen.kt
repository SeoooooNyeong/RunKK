package com.a207.runkk.app.ui.me

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
    "닉네임·출생연도·성별·사진·소개 등 회원 정보",
    "러닝 기록(위치 경로·심박 포함)과 목표·리포트",
    "크루 소속·캐릭터·리워드·알림함",
    "이 기기에 남은 미전송 기록"
)

private val KEPT = listOf("대결 기록, 크루 주간 거리 기여분, 응원보드·크루런 글은 \"탈퇴한 사용자\"로 남아요")

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
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = "회원 탈퇴", onBack = onBack)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "떠나기 전에 확인해 주세요",
                    style = RunkkTextStyles.Headline,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                RunkkCard {
                    Text("7일 안에 지워져요", style = RunkkTextStyles.BodyStrong, color = RunkkColors.Lavender)
                    DELETED.forEach { Text("· $it", style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary) }
                }
                RunkkCard {
                    Text("남는 것", style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextSecondary)
                    KEPT.forEach { Text("· $it", style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary) }
                }
                Text(
                    "진행 중인 대결·매칭 대기가 있거나 크루원이 있는 크루장이면 탈퇴할 수 없어요.",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                RunkkCheckRow(
                    label = "위 내용을 모두 확인했어요",
                    checked = state.acknowledged,
                    onCheckedChange = onAcknowledgedChange
                )
            }
            RunkkAccentOutlinedButton(
                text = if (state.withdrawing) "탈퇴하는 중…" else "탈퇴하기",
                onClick = { confirm = true },
                enabled = state.acknowledged && !state.withdrawing,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
    if (confirm) {
        RunkkConfirmDialog(
            title = "정말 탈퇴할까요?",
            message = "탈퇴하면 되돌릴 수 없어요.",
            confirmLabel = "탈퇴",
            onConfirm = {
                confirm = false
                onWithdraw()
            },
            onDismiss = { confirm = false },
            destructive = true
        )
    }
}

@Preview(name = "회원 탈퇴", widthDp = 390, heightDp = 844)
@Composable
private fun WithdrawPreview() {
    RunkkTheme(reduceMotion = true) { WithdrawScreen(WithdrawUiState(acknowledged = true), {}, {}, {}, {}) }
}

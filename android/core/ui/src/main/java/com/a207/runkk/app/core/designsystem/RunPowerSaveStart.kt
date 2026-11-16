package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 러닝 출발 전 절전 모드 안내([임시:FR-성-380]~[임시:FR-성-382], [임시:D-성-901]). 돌려준 함수를 출발 대신 부르면 절전 모드가 켜져
 * 있을 때 [onStart]를 보류하고 안내를 띄운다. 꺼져 있거나 이 화면에서 이미 안내했으면 바로 [onStart]를 부른다.
 *
 * - 「그냥 시작」은 안내를 닫고 [onStart]를 한 번 부른다. 안내 중에 다시 부른 출발은 무시한다.
 * - 「설정 열기」는 안내를 둔 채 설정을 연다. 돌아왔을 때 절전 모드가 꺼졌으면 [onStart]를 잇고, 켜져 있으면 안내에서 다시 고른다.
 *   열 수 있는 설정 화면이 없으면 바로 출발한다.
 * - 뒤로 가기·바깥 누름은 출발하지 않고 [onCancel]을 부른다(시작 전 매칭 화면이면 앞 화면으로 돌아간다).
 *
 * 안내를 본 상태는 화면 회전에도 지킨다. 원래 출발 처리는 최신 람다를 쓰며 저장 상태에 람다를 넣지 않는다.
 *
 * ```
 * val start = rememberRunPowerSaveStart(onStart = viewModel::onStart)
 * RunkkPrimaryButton("시작", onClick = start)
 * ```
 */
@Composable
fun rememberRunPowerSaveStart(onCancel: () -> Unit = {}, onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentStart by rememberUpdatedState(onStart)
    val currentCancel by rememberUpdatedState(onCancel)
    var isPromptShown by rememberSaveable { mutableStateOf(false) }
    var isPromptVisible by rememberSaveable { mutableStateOf(false) }
    val proceed = {
        isPromptVisible = false
        currentStart()
    }
    // 설정에서 돌아왔을 때 절전 모드를 껐으면 보류한 출발을 잇는다.
    LifecycleResumeEffect(context) {
        if (isPromptVisible && !shouldOfferRunPowerSaveSettings(context)) proceed()
        onPauseOrDispose {}
    }
    if (isPromptVisible) {
        RunPowerSaveDialog(
            onSettings = { if (!openRunPowerSaveSettings(context)) proceed() },
            onStart = proceed,
            onDismissRequest = {
                isPromptVisible = false
                currentCancel()
            }
        )
    }
    return remember(context) {
        {
            when {
                isPromptVisible -> Unit

                !isPromptShown && shouldOfferRunPowerSaveSettings(context) -> {
                    isPromptShown = true
                    isPromptVisible = true
                }

                else -> currentStart()
            }
        }
    }
}

/**
 * 절전 모드 안내 대화상자([임시:NFR-성-20]): 제목 「절전 모드를 꺼 주세요」, 한 문장 본문, 왼쪽 「그냥 시작」과 오른쪽 기본 단추 「설정 열기」.
 * 디자인 시스템 확인 대화상자를 그대로 쓴다. [onDismissRequest]는 뒤로 가기·바깥 누름이다.
 */
@Composable
fun RunPowerSaveDialog(onSettings: () -> Unit, onStart: () -> Unit, onDismissRequest: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(R.string.core_run_power_save_title),
        message = stringResource(R.string.core_run_power_save_message),
        confirmLabel = stringResource(R.string.core_run_power_save_settings),
        onConfirm = onSettings,
        onDismiss = onStart,
        dismissLabel = stringResource(R.string.core_run_power_save_start),
        onDismissRequest = onDismissRequest
    )
}

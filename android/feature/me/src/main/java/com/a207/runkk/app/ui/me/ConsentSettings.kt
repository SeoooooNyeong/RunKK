package com.a207.runkk.app.ui.me

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow

/**
 * 동의 관리 묶음(F4, FR-011~FR-016): 위치, 심박(선택), AI 리포트·코치 전송(선택), 얼굴 변환 전송(선택), 마케팅(선택).
 * 위치·심박 철회는 확인을 거치고, 심박 동의는 민감정보 안내를 먼저 보인다(FR-014).
 */
@Composable
internal fun ConsentSection(state: SettingsUiState, actions: SettingsActions) {
    var confirmWithdraw by remember { mutableStateOf<String?>(null) }
    var isAskingHeartRate by remember { mutableStateOf(false) }
    var isAskingLlm by remember { mutableStateOf(false) }
    Section(R.string.me_settings_section_consents) {
        RunkkSwitchRow(
            stringResource(R.string.me_consents_location_title),
            state.consent(ConsentKeys.LOCATION),
            { on -> if (on) actions.setConsent(ConsentKeys.LOCATION, true) else confirmWithdraw = ConsentKeys.LOCATION }
        )
        SettingsDivider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_heart_title),
            state.consent(ConsentKeys.HEART_RATE),
            { on -> if (on) isAskingHeartRate = true else confirmWithdraw = ConsentKeys.HEART_RATE }
        )
        SettingsDivider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_llm_title),
            state.consent(ConsentKeys.LLM_TRANSFER),
            { on -> if (on) isAskingLlm = true else actions.setConsent(ConsentKeys.LLM_TRANSFER, false) },
            description = stringResource(R.string.me_consents_llm_summary)
        )
        SettingsDivider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_face_title),
            state.consent(ConsentKeys.FACE_TRANSFER),
            { actions.setConsent(ConsentKeys.FACE_TRANSFER, it) }
        )
        SettingsDivider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_marketing_title),
            state.consent(ConsentKeys.MARKETING),
            { actions.setConsent(ConsentKeys.MARKETING, it) }
        )
    }
    confirmWithdraw?.let { item ->
        WithdrawConsentDialog(
            item,
            onConfirm = {
                confirmWithdraw = null
                actions.setConsent(item, false)
            },
            onDismiss = { confirmWithdraw = null }
        )
    }
    if (isAskingHeartRate) {
        HeartRateConsentDialog(
            onAgree = {
                isAskingHeartRate = false
                actions.setConsent(ConsentKeys.HEART_RATE, true)
            },
            onDismiss = { isAskingHeartRate = false }
        )
    }
    if (isAskingLlm) {
        RunkkConfirmDialog(
            title = stringResource(R.string.me_consents_llm_dialog_title),
            message = stringResource(R.string.me_consents_llm_message),
            confirmLabel = stringResource(R.string.me_consents_llm_dialog_agree),
            onConfirm = {
                isAskingLlm = false
                actions.setConsent(ConsentKeys.LLM_TRANSFER, true)
            },
            onDismiss = { isAskingLlm = false }
        )
    }
}

/** 위치·심박 동의 철회 확인. */
@Composable
private fun WithdrawConsentDialog(item: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val isLocation = item == ConsentKeys.LOCATION
    RunkkConfirmDialog(
        title = stringResource(
            if (isLocation) R.string.me_consents_withdraw_location_title else R.string.me_consents_withdraw_heart_title
        ),
        message = stringResource(
            if (isLocation) {
                R.string.me_consents_withdraw_location_message
            } else {
                R.string.me_consents_withdraw_heart_message
            }
        ),
        confirmLabel = stringResource(R.string.me_consents_withdraw_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isDestructive = true
    )
}

/** 심박 수집 동의(FR-014): 민감정보임을 먼저 보인다. */
@Composable
internal fun HeartRateConsentDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(R.string.me_consents_heart_dialog_title),
        message = stringResource(R.string.me_consents_heart_dialog_message),
        confirmLabel = stringResource(R.string.me_consents_heart_dialog_agree),
        onConfirm = onAgree,
        onDismiss = onDismiss
    )
}

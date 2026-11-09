package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar

/**
 * 동의 관리 묶음(F4, FR-011~FR-016): 위치, 심박(선택), AI 리포트·코치 전송(선택), 얼굴 변환 전송(선택), 마케팅(선택).
 * 위치·심박 철회는 확인을 거치고, 심박 동의는 민감정보 안내를 먼저 보인다(FR-014).
 */
@Composable
internal fun ConsentSection(
    state: SettingsUiState,
    actions: SettingsActions,
    fromCoach: Boolean,
    onOpenLlmNotice: () -> Unit
) {
    var confirmWithdraw by remember { mutableStateOf<String?>(null) }
    var isAskingHeartRate by remember { mutableStateOf(false) }
    if (fromCoach) {
        ConsentCard(
            R.string.me_consents_coach_title,
            R.string.me_consents_coach_summary,
            R.string.me_consents_coach_return
        )
    }
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
            !fromCoach && state.consent(ConsentKeys.LLM_TRANSFER),
            { on -> if (on) onOpenLlmNotice() else actions.setConsent(ConsentKeys.LLM_TRANSFER, false) },
            description = stringResource(R.string.me_consents_llm_summary),
            isEnabled = !state.isSavingLlmConsent
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
}

/** G-35. 기존 동의 관리 경로 안에서 최신 고지를 확인한다. */
@Composable
internal fun LlmConsentNotice(onCancel: () -> Unit, onAgree: () -> Unit) {
    Column(Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(
            title = stringResource(R.string.me_consents_llm_notice_title),
            onBack = onCancel,
            isTitleCentered = true
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp)) {
            ConsentCard(
                R.string.me_consents_llm_dialog_title,
                R.string.me_consents_llm_message,
                R.string.me_consents_llm_retention
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), Arrangement.spacedBy(8.dp)) {
            RunkkSecondaryButton(stringResource(R.string.me_consents_llm_cancel), onCancel, Modifier.weight(1f))
            RunkkPrimaryButton(stringResource(R.string.me_consents_llm_dialog_agree), onAgree, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConsentCard(title: Int, summary: Int, detail: Int) {
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(summary), style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
        Text(stringResource(detail), style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
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

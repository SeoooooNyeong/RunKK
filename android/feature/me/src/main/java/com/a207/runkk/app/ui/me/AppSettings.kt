package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.navigation.Routes

/**
 * 설정(F4): 계정, 표시, 공개 범위, 동의 관리, 기기, 기타 묶음과 맨 아래 로그아웃·회원 탈퇴.
 * 서버가 거부한 기록이 있으면 맨 위에 기록 전송 묶음(못 올린 기록 n건, 다시 보내기)을 보인다(W40).
 * 연동하지 않은 게스트의 로그아웃은 기기 기록이 지워진다는 확인과 연동 길을 보인다(F10, FR-199).
 */
@Composable
internal fun AppSettings(state: SettingsUiState, actions: SettingsActions) {
    var isAskingHeartRate by remember { mutableStateOf(false) }
    var isChoosingVisibility by remember { mutableStateOf(false) }
    var isShowingLegal by remember { mutableStateOf(false) }
    var isConfirmingLogout by remember { mutableStateOf(false) }
    if (state.rejectedRunCount > 0) UploadSection(state, actions)
    AccountSection(state, actions)
    DisplaySection(state, actions)
    SoundSection()
    VisibilitySection(state, actions, onChooseVisibility = { isChoosingVisibility = true })
    ConsentSection(state, actions)
    DeviceSection(
        state,
        actions,
        onAskHeartRate = { isAskingHeartRate = true },
        onLegal = { isShowingLegal = true }
    )
    AccountActions(onLogout = { isConfirmingLogout = true }, onWithdraw = { actions.onNavigate(Routes.WITHDRAW) })
    if (isAskingHeartRate) {
        HeartRateConsentDialog(
            onAgree = {
                isAskingHeartRate = false
                actions.setConsent(ConsentKeys.HEART_RATE, true)
                actions.setValue(SettingKeys.WATCH_LINKED, "true")
            },
            onDismiss = { isAskingHeartRate = false }
        )
    }
    if (isChoosingVisibility) {
        ChoiceDialog(
            R.string.me_settings_record_visibility_title,
            RecordVisibilityOptions.values,
            state.value(SettingKeys.RECORD_VISIBILITY),
            onSelect = {
                isChoosingVisibility = false
                actions.setValue(SettingKeys.RECORD_VISIBILITY, it)
            },
            onDismiss = { isChoosingVisibility = false }
        )
    }
    if (isShowingLegal) {
        LegalListDialog(
            onView = { type, title ->
                isShowingLegal = false
                actions.onViewLegal(type, UiText.Res(title))
            },
            onDismiss = { isShowingLegal = false }
        )
    }
    if (isConfirmingLogout) {
        LogoutDialog(
            isGuest = state.isGuest,
            onConfirm = {
                isConfirmingLogout = false
                actions.onLogout()
            },
            onLink = {
                isConfirmingLogout = false
                actions.onNavigate(MeRoutes.ACCOUNT_LINK)
            },
            onDismiss = { isConfirmingLogout = false }
        )
    }
}

/** 기록 전송 묶음(W40): 서버가 거부해 멈춘 폰 기록 수와 다시 보내기. 누르면 폰과 연결된 워치의 거부 기록을 다시 보낸다. */
@Composable
private fun UploadSection(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.me_settings_section_upload) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.me_settings_rejected_runs, state.rejectedRunCount),
                style = RunkkTextStyles.text(15, FontWeight.Medium),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            RunkkTextLink(
                stringResource(R.string.me_settings_resend),
                onClick = { if (!state.isResending) actions.onResendRejected() }
            )
        }
    }
}

/** 계정 묶음(FR-198): 계정 유형, 게스트면 소셜 계정 연동(F9)으로 가는 줄. */
@Composable
private fun AccountSection(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.me_settings_section_account) {
        ValueRow(
            stringResource(R.string.me_settings_account_type),
            stringResource(if (state.isGuest) R.string.me_account_type_guest else R.string.me_account_type_social)
        )
        if (state.isGuest) {
            SettingsDivider()
            ValueRow(
                stringResource(R.string.me_settings_page_account_link),
                stringResource(R.string.me_settings_account_link_providers),
                onClick = { actions.onNavigate(MeRoutes.ACCOUNT_LINK) }
            )
        }
    }
}

/** 표시 묶음: 화면 테마(FR-203, 고르면 바로 바뀐다 D-92), 거리 단위. */
@Composable
private fun DisplaySection(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.me_settings_section_display) {
        InlineChoiceRow(
            R.string.me_settings_theme_title,
            SettingChoices.theme,
            state.value(SettingKeys.THEME),
            optionWidth = 64.dp
        ) { actions.setValue(SettingKeys.THEME, it) }
        SettingsDivider()
        InlineChoiceRow(
            R.string.me_settings_unit_title,
            SettingChoices.unit,
            state.value(SettingKeys.UNIT),
            optionWidth = 52.dp
        ) { actions.setValue(SettingKeys.UNIT, it) }
    }
}

/** 공개 범위 묶음: 기록 공개 범위(값 줄 → 고르기), 코스 지도 공유, 프로필 정보 공개. */
@Composable
private fun VisibilitySection(state: SettingsUiState, actions: SettingsActions, onChooseVisibility: () -> Unit) {
    val visibility = state.value(SettingKeys.RECORD_VISIBILITY)
    val visibilityLabel = RecordVisibilityOptions.values.firstOrNull { it.first == visibility }?.second
    Section(R.string.me_settings_section_visibility) {
        ValueRow(
            stringResource(R.string.me_settings_record_visibility_title),
            visibilityLabel?.let { stringResource(it) },
            onClick = onChooseVisibility
        )
        SettingsDivider()
        RunkkSwitchRow(
            title = stringResource(R.string.me_settings_course_map_title),
            isChecked = state.flag(SettingKeys.COURSE_MAP_SHARED),
            onCheckedChange = { actions.setValue(SettingKeys.COURSE_MAP_SHARED, it.toString()) },
            // E2E(android/e2e/maestro) 선택자: 켜짐 여부(checked)는 글자가 아닌 줄 전체에 붙는다.
            modifier = Modifier.testTag("setting_course_map_shared")
        )
        SettingsDivider()
        RunkkSwitchRow(
            title = stringResource(R.string.me_settings_profile_visibility_title),
            isChecked = state.value(SettingKeys.PROFILE_VISIBILITY) == PROFILE_PUBLIC,
            onCheckedChange = {
                actions.setValue(SettingKeys.PROFILE_VISIBILITY, if (it) PROFILE_PUBLIC else PROFILE_PRIVATE)
            }
        )
    }
}

/** 기기, 기타 묶음: 워치 연동(심박 동의가 없으면 [onAskHeartRate]), 알림 설정, 약관 및 정책, 도움말. */
@Composable
private fun DeviceSection(
    state: SettingsUiState,
    actions: SettingsActions,
    onAskHeartRate: () -> Unit,
    onLegal: () -> Unit
) {
    Section(R.string.me_settings_section_device) {
        RunkkSwitchRow(
            title = stringResource(R.string.me_settings_watch_title),
            isChecked = state.flag(SettingKeys.WATCH_LINKED),
            onCheckedChange = { on ->
                if (on && !state.consent(ConsentKeys.HEART_RATE)) {
                    onAskHeartRate()
                } else {
                    actions.setValue(SettingKeys.WATCH_LINKED, on.toString())
                }
            }
        )
        SettingsDivider()
        ValueRow(
            stringResource(R.string.me_settings_page_notifications),
            null,
            onClick = { actions.onNavigate(Routes.NOTIFICATION_SETTINGS) }
        )
        SettingsDivider()
        ValueRow(stringResource(R.string.me_settings_legal_title), null, onClick = onLegal)
    }
}

/** 맨 아래 로그아웃·회원 탈퇴. */
@Composable
private fun AccountActions(onLogout: () -> Unit, onWithdraw: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onLogout) {
            Text(stringResource(R.string.me_menu_logout), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        }
        TextButton(onClick = onWithdraw) {
            Text(
                stringResource(R.string.me_menu_withdraw),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 약관 및 정책, 도움말: 문서를 골라 연다(FR-018). */
@Composable
private fun LegalListDialog(onView: (String, Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RunkkColors.Surface,
        title = {
            Text(
                stringResource(R.string.me_settings_legal_title),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary
            )
        },
        text = {
            Column {
                LEGAL_DOCUMENTS.forEach { (type, title) -> LegalDocumentRow(title, onClick = { onView(type, title) }) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.me_legal_close), color = RunkkColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun LegalDocumentRow(@StringRes title: Int, onClick: () -> Unit) {
    val text = stringResource(title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        RunkkTextLink(
            stringResource(R.string.me_legal_view),
            onClick = onClick,
            contentDescription = stringResource(R.string.me_legal_view_description, text)
        )
    }
}

private val LEGAL_DOCUMENTS = listOf(
    LegalTypes.TERMS to R.string.me_legal_terms,
    LegalTypes.PRIVACY_POLICY to R.string.me_legal_privacy,
    LegalTypes.LOCATION_TERMS to R.string.me_legal_location_terms,
    LegalTypes.HELP to R.string.me_legal_help
)

private const val PROFILE_PUBLIC = "PUBLIC"
private const val PROFILE_PRIVATE = "PRIVATE"

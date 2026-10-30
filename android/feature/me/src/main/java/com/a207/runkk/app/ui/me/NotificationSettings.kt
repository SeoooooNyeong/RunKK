package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow

/**
 * 알림 설정(F5, FR-008): 러닝, 대결 / 크루 / 기타 묶음. 스위치 하나가 묶인 알림 종류를 함께 켜고 끈다.
 * 1차 배포에서 닫은 기능(크루, 아바타)의 스위치는 숨기고, 스위치가 모두 숨으면 묶음도 그리지 않는다(사용자 요청 2026-10-31).
 */
@Composable
internal fun NotificationSettings(state: SettingsUiState, actions: SettingsActions) {
    NotificationSection(R.string.me_settings_section_alert_running, NotificationTypes.running, state, actions)
    NotificationSection(R.string.me_settings_section_alert_crew, NotificationTypes.crew, state, actions)
    NotificationSection(R.string.me_settings_section_alert_etc, NotificationTypes.etc, state, actions)
}

@Composable
private fun NotificationSection(
    @StringRes title: Int,
    groups: List<NotificationGroup>,
    state: SettingsUiState,
    actions: SettingsActions
) {
    val scope = LocalReleaseScope.current
    val shown = groups.filter { group -> group.requiredFeature?.let(scope::isOpen) ?: true }
    if (shown.isEmpty()) return
    Section(title) {
        shown.forEachIndexed { index, group ->
            RunkkSwitchRow(
                title = stringResource(group.titleRes),
                isChecked = group.isOn(state),
                onCheckedChange = { on -> group.set(on, actions) },
                description = group.descriptionRes?.let { stringResource(it) }
            )
            if (index < shown.lastIndex) SettingsDivider()
        }
    }
}

/** 묶인 알림 종류가 모두 켜져 있으면 켬. 동의 묶음이면 그 동의 값. */
private fun NotificationGroup.isOn(state: SettingsUiState): Boolean =
    consentItem?.let(state::consent) ?: types.all { state.flag(SettingKeys.alert(it)) }

private fun NotificationGroup.set(isOn: Boolean, actions: SettingsActions) {
    consentItem?.let {
        actions.setConsent(it, isOn)
        return
    }
    types.forEach { actions.setValue(SettingKeys.alert(it), isOn.toString()) }
}

package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.account.MemberSettings

/** 설정 화면 종류. 같은 SettingsViewModel을 화면마다 따로 둔다. */
enum class SettingsPage(@StringRes val titleRes: Int) {
    APP(R.string.me_settings_page_app),
    FREE_RUN(R.string.me_settings_page_free_run),
    NOTIFICATIONS(R.string.me_settings_page_notifications),
    CONSENTS(R.string.me_settings_page_consents)
}

/** 설정 화면 사용자 행동. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val setValue: (String, String) -> Unit = { _, _ -> },
    val setConsent: (String, Boolean) -> Unit = { _, _ -> },
    val onViewLegal: (String, UiText) -> Unit = { _, _ -> },
    val onLegalDismissed: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

@Composable
fun SettingsRoute(page: SettingsPage, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(state.urlToOpen) {
        state.urlToOpen?.let {
            runCatching { uriHandler.openUri(it) }
            viewModel.onUrlOpened()
        }
    }
    SettingsScreen(
        page = page,
        state = state,
        actions = SettingsActions(
            onBack = navigator::back,
            onRetry = viewModel::load,
            setValue = viewModel::setValue,
            setConsent = viewModel::setConsent,
            onViewLegal = viewModel::onViewLegal,
            onLegalDismissed = viewModel::onLegalDismissed,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

@Composable
fun SettingsScreen(
    page: SettingsPage,
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.asString()
    LaunchedEffect(state.message) {
        messageText?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(page.titleRes), onBack = actions.onBack)
            when {
                state.isLoading -> RunkkLoading(modifier = Modifier.weight(1f))

                state.settings == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.me_settings_load_failed),
                    onRetry = actions.onRetry
                )

                else -> Column(
                    modifier = Modifier.weight(
                        1f
                    ).verticalScroll(
                        rememberScrollState()
                    ).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when (page) {
                        SettingsPage.APP -> AppSettings(state, actions)
                        SettingsPage.FREE_RUN -> FreeRunSettings(state, actions)
                        SettingsPage.NOTIFICATIONS -> NotificationSettings(state, actions)
                        SettingsPage.CONSENTS -> ConsentSettings(state, actions)
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
    state.legal?.let { LegalDialog(it, actions.onLegalDismissed) }
}

/** 고지문서 본문 대화상자(설정·가입 화면이 함께 쓴다). */
@Composable
internal fun LegalDialog(legal: LegalView, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RunkkColors.Surface,
        title = { Text(legal.title.asString(), style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary) },
        text = {
            Text(
                legal.body,
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.me_legal_close), color = RunkkColors.Lime) }
        }
    )
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSectionTitle(stringResource(title), modifier = Modifier.padding(horizontal = 8.dp))
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}

@Composable
private fun ChoiceSetting(
    @StringRes title: Int,
    @StringRes description: Int?,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(title),
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary
        )
        if (description != null) {
            Text(stringResource(description), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        RunkkSegmentRow(
            options = options.map { stringResource(it.second) },
            selectedIndex = options.indexOfFirst { it.first == selected },
            onSelect = { onSelect(options[it].first) }
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = RunkkColors.Border)

@Composable
private fun ColumnScope.AppSettings(state: SettingsUiState, actions: SettingsActions) {
    var askHeartRate by remember { mutableStateOf(false) }
    RecordSettingsSection(state, actions)
    UnitDeviceSection(state, actions, onAskHeartRate = { askHeartRate = true })
    LegalSection(actions)
    if (askHeartRate) {
        HeartRateConsentDialog(
            onAgree = {
                askHeartRate = false
                actions.setConsent(HEART_RATE, true)
                actions.setValue(SettingKeys.WATCH_LINKED, "true")
            },
            onDismiss = { askHeartRate = false }
        )
    }
}

/** 기록·공개 묶음: 기록 공개 범위, 코스 지도 공유, 개인정보 공개. */
@Composable
private fun RecordSettingsSection(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.me_settings_section_record) {
        ChoiceSetting(
            R.string.me_settings_record_visibility_title,
            R.string.me_settings_record_visibility_description,
            RecordVisibilityOptions.values,
            state.value(SettingKeys.RECORD_VISIBILITY)
        ) {
            actions.setValue(SettingKeys.RECORD_VISIBILITY, it)
        }
        Divider()
        RunkkSwitchRow(
            title = stringResource(R.string.me_settings_course_map_title),
            description = stringResource(R.string.me_settings_course_map_description),
            isChecked = state.flag(SettingKeys.COURSE_MAP_SHARED),
            onCheckedChange = { actions.setValue(SettingKeys.COURSE_MAP_SHARED, it.toString()) },
            // E2E(android/e2e/maestro) 선택자: 켜짐 여부(checked)는 글자가 아닌 줄 전체에 붙는다.
            modifier = Modifier.testTag("setting_course_map_shared")
        )
        Divider()
        ChoiceSetting(
            R.string.me_settings_profile_visibility_title,
            R.string.me_settings_profile_visibility_description,
            SettingChoices.profileVisibility,
            state.value(SettingKeys.PROFILE_VISIBILITY)
        ) {
            actions.setValue(SettingKeys.PROFILE_VISIBILITY, it)
        }
    }
}

/** 단위·기기 묶음: 거리 단위, 워치 연동(심박 동의가 없으면 [onAskHeartRate]). */
@Composable
private fun UnitDeviceSection(state: SettingsUiState, actions: SettingsActions, onAskHeartRate: () -> Unit) {
    Section(R.string.me_settings_section_unit_device) {
        ChoiceSetting(R.string.me_settings_unit_title, null, SettingChoices.unit, state.value(SettingKeys.UNIT)) {
            actions.setValue(SettingKeys.UNIT, it)
        }
        Divider()
        RunkkSwitchRow(
            title = stringResource(R.string.me_settings_watch_title),
            description = stringResource(R.string.me_settings_watch_description),
            isChecked = state.flag(SettingKeys.WATCH_LINKED),
            onCheckedChange = { on ->
                if (on &&
                    !state.consent(HEART_RATE)
                ) {
                    onAskHeartRate()
                } else {
                    actions.setValue(SettingKeys.WATCH_LINKED, on.toString())
                }
            }
        )
    }
}

/** 고지문서 묶음. */
@Composable
private fun LegalSection(actions: SettingsActions) {
    Section(R.string.me_settings_section_legal) {
        LegalRow(R.string.me_legal_terms, LegalTypes.TERMS, actions)
        Divider()
        LegalRow(R.string.me_legal_privacy, LegalTypes.PRIVACY_POLICY, actions)
        Divider()
        LegalRow(R.string.me_legal_location_terms, LegalTypes.LOCATION_TERMS, actions)
        Divider()
        LegalRow(R.string.me_legal_help, LegalTypes.HELP, actions)
    }
}

@Composable
private fun LegalRow(@StringRes titleRes: Int, type: String, actions: SettingsActions) {
    val title = stringResource(titleRes)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        RunkkTextLink(
            stringResource(R.string.me_legal_view),
            onClick = { actions.onViewLegal(type, UiText.Res(titleRes)) },
            contentDescription = stringResource(R.string.me_legal_view_description, title)
        )
    }
}

@Composable
private fun ColumnScope.FreeRunSettings(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.me_settings_section_free_run) {
        ChoiceSetting(
            R.string.me_settings_target_title,
            R.string.me_settings_target_description,
            FreeRunTargets.values,
            state.value(SettingKeys.RUN_TARGET)
        ) {
            actions.setValue(SettingKeys.RUN_TARGET, it)
        }
        Divider()
        RunkkSwitchRow(stringResource(R.string.me_settings_voice_title), state.flag(SettingKeys.RUN_VOICE), {
            actions.setValue(SettingKeys.RUN_VOICE, it.toString())
        }, description = stringResource(R.string.me_settings_free_run_voice_description))
        Divider()
        RunkkSwitchRow(stringResource(R.string.me_settings_vibration_title), state.flag(SettingKeys.RUN_VIBRATION), {
            actions.setValue(SettingKeys.RUN_VIBRATION, it.toString())
        })
        Divider()
        RunkkSwitchRow(
            stringResource(R.string.me_settings_follow_plan_title),
            state.flag(SettingKeys.RUN_FOLLOW_COACH_PLAN),
            { actions.setValue(SettingKeys.RUN_FOLLOW_COACH_PLAN, it.toString()) },
            description = stringResource(R.string.me_settings_follow_plan_description)
        )
    }
    Section(R.string.me_settings_section_battle) {
        RunkkSwitchRow(stringResource(R.string.me_settings_voice_title), state.flag(SettingKeys.BATTLE_VOICE), {
            actions.setValue(SettingKeys.BATTLE_VOICE, it.toString())
        }, description = stringResource(R.string.me_settings_battle_voice_description))
        Divider()
        RunkkSwitchRow(stringResource(R.string.me_settings_vibration_title), state.flag(SettingKeys.BATTLE_VIBRATION), {
            actions.setValue(SettingKeys.BATTLE_VIBRATION, it.toString())
        })
        Divider()
        ChoiceSetting(
            R.string.me_settings_gap_title,
            null,
            GapAlertIntervals.values,
            state.value(SettingKeys.BATTLE_GAP_ALERT)
        ) {
            actions.setValue(SettingKeys.BATTLE_GAP_ALERT, it)
        }
    }
}

@Composable
private fun ColumnScope.NotificationSettings(state: SettingsUiState, actions: SettingsActions) {
    Text(
        stringResource(R.string.me_settings_coaching_notice),
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
    Section(R.string.me_settings_section_alerts) {
        NotificationTypes.values.forEachIndexed { index, (type, label) ->
            RunkkSwitchRow(stringResource(label), state.flag(SettingKeys.alert(type)), {
                actions.setValue(SettingKeys.alert(type), it.toString())
            })
            if (index < NotificationTypes.values.lastIndex) Divider()
        }
    }
}

@Composable
private fun ColumnScope.ConsentSettings(state: SettingsUiState, actions: SettingsActions) {
    var confirmWithdraw by remember { mutableStateOf<String?>(null) }
    var askHeartRate by remember { mutableStateOf(false) }
    RequiredConsentSection(actions)
    WithdrawableConsentSection(
        state,
        actions,
        onWithdraw = { confirmWithdraw = it },
        onAskHeartRate = { askHeartRate = true }
    )
    confirmWithdraw?.let { item ->
        val location = item == LOCATION
        val titleRes =
            if (location) R.string.me_consents_withdraw_location_title else R.string.me_consents_withdraw_heart_title
        val messageRes = if (location) {
            R.string.me_consents_withdraw_location_message
        } else {
            R.string.me_consents_withdraw_heart_message
        }
        RunkkConfirmDialog(
            title = stringResource(titleRes),
            message = stringResource(messageRes),
            confirmLabel = stringResource(R.string.me_consents_withdraw_confirm),
            onConfirm = {
                confirmWithdraw = null
                actions.setConsent(item, false)
            },
            onDismiss = { confirmWithdraw = null },
            isDestructive = true
        )
    }
    if (askHeartRate) {
        HeartRateConsentDialog(
            onAgree = {
                askHeartRate = false
                actions.setConsent(HEART_RATE, true)
            },
            onDismiss = { askHeartRate = false }
        )
    }
}

/** 필수 동의 묶음(철회 불가, 약관·방침 보기). */
@Composable
private fun RequiredConsentSection(actions: SettingsActions) {
    Section(R.string.me_consents_section_required) {
        RequiredConsentRow(R.string.me_consents_age_confirm)
        Divider()
        RequiredConsentRow(
            R.string.me_legal_terms,
            onView = { actions.onViewLegal(LegalTypes.TERMS, UiText.Res(R.string.me_legal_terms)) }
        )
        Divider()
        RequiredConsentRow(
            R.string.me_consents_privacy_collect,
            onView = {
                actions.onViewLegal(LegalTypes.PRIVACY_POLICY, UiText.Res(R.string.me_consents_privacy_collect))
            }
        )
    }
}

/** 철회할 수 있는 동의 묶음. 위치·심박은 철회 확인([onWithdraw])·심박 동의 안내([onAskHeartRate])를 거친다. */
@Composable
private fun WithdrawableConsentSection(
    state: SettingsUiState,
    actions: SettingsActions,
    onWithdraw: (String) -> Unit,
    onAskHeartRate: () -> Unit
) {
    Section(R.string.me_consents_section_withdrawable) {
        RunkkSwitchRow(
            stringResource(R.string.me_consents_location_title),
            state.consent(LOCATION),
            { on -> if (on) actions.setConsent(LOCATION, true) else onWithdraw(LOCATION) },
            description = stringResource(R.string.me_consents_location_description)
        )
        Divider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_heart_title),
            state.consent(HEART_RATE),
            { on -> if (on) onAskHeartRate() else onWithdraw(HEART_RATE) },
            description = stringResource(R.string.me_consents_heart_description)
        )
        Divider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_llm_title),
            state.consent(ConsentKeys.LLM_TRANSFER),
            { actions.setConsent(ConsentKeys.LLM_TRANSFER, it) },
            description = stringResource(R.string.me_consents_llm_description)
        )
        Divider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_face_title),
            state.consent(ConsentKeys.FACE_TRANSFER),
            { actions.setConsent(ConsentKeys.FACE_TRANSFER, it) },
            description = stringResource(R.string.me_consents_face_description)
        )
        Divider()
        RunkkSwitchRow(
            stringResource(R.string.me_consents_marketing_title),
            state.consent(ConsentKeys.MARKETING),
            { actions.setConsent(ConsentKeys.MARKETING, it) }
        )
    }
}

@Composable
private fun RequiredConsentRow(@StringRes titleRes: Int, onView: (() -> Unit)? = null) {
    val title = stringResource(titleRes)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RunkkTextStyles.text(15, FontWeight.Medium), color = RunkkColors.TextPrimary)
            Text(
                stringResource(R.string.me_consents_required_status),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        if (onView != null) {
            RunkkTextLink(
                stringResource(R.string.me_legal_view),
                onClick = onView,
                contentDescription = stringResource(R.string.me_legal_view_description, title)
            )
        }
    }
}

/** 심박 수집 동의(FR-014): 민감정보와 사용 목적을 먼저 보인다. */
@Composable
private fun HeartRateConsentDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(R.string.me_consents_heart_dialog_title),
        message = stringResource(R.string.me_consents_heart_dialog_message),
        confirmLabel = stringResource(R.string.me_consents_heart_dialog_agree),
        onConfirm = onAgree,
        onDismiss = onDismiss
    )
}

private const val LOCATION = ConsentKeys.LOCATION
private const val HEART_RATE = ConsentKeys.HEART_RATE

@Preview(name = "자유런 설정", widthDp = 390, heightDp = 1000)
@Composable
private fun FreeRunSettingsPreview() {
    RunkkTheme(reduceMotion = true) {
        SettingsScreen(
            page = SettingsPage.FREE_RUN,
            state = SettingsUiState(
                isLoading = false,
                settings = MemberSettings(mapOf(SettingKeys.RUN_TARGET to "KM5"), emptyMap())
            ),
            actions = SettingsActions()
        )
    }
}

@Preview(name = "동의 관리", widthDp = 390, heightDp = 1000)
@Composable
private fun ConsentsPreview() {
    RunkkTheme(reduceMotion = true) {
        SettingsScreen(
            page = SettingsPage.CONSENTS,
            state = SettingsUiState(
                isLoading = false,
                settings = MemberSettings(emptyMap(), mapOf("LOCATION" to true))
            ),
            actions = SettingsActions()
        )
    }
}

package com.a207.runkk.app.ui.me

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
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.account.MemberSettings

/** 설정 화면 종류. 같은 SettingsViewModel을 화면마다 따로 둔다. */
enum class SettingsPage(val title: String) {
    APP("앱 설정"),
    FREE_RUN("자유런 설정"),
    NOTIFICATIONS("알림 설정"),
    CONSENTS("동의 관리")
}

/** 설정 화면 사용자 행동. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val setValue: (String, String) -> Unit = { _, _ -> },
    val setConsent: (String, Boolean) -> Unit = { _, _ -> },
    val onViewLegal: (String, String) -> Unit = { _, _ -> },
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
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = page.title, onBack = actions.onBack)
            when {
                state.loading -> RunkkLoading(modifier = Modifier.weight(1f))

                state.settings == null -> RunkkErrorState(state.error ?: "설정을 불러오지 못했어요", onRetry = actions.onRetry)

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
    state.legal?.let { legal ->
        AlertDialog(
            onDismissRequest = actions.onLegalDismissed,
            containerColor = RunkkColors.Surface,
            title = { Text(legal.title, style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary) },
            text = {
                Text(
                    legal.body,
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = actions.onLegalDismissed) { Text("닫기", color = RunkkColors.Lime) } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSectionTitle(title, modifier = Modifier.padding(horizontal = 8.dp))
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}

@Composable
private fun ChoiceSetting(
    title: String,
    description: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = RunkkTextStyles.text(15, FontWeight.Medium), color = RunkkColors.TextPrimary)
        if (description != null) Text(description, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        RunkkSegmentRow(
            options = options.map {
                it.second
            },
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
    Section("기록·공개") {
        ChoiceSetting(
            "기록 공개 범위",
            "바꾼 범위는 지난 기록에도 적용돼요. 고스트 대결 상대는 전체공개 기록만 돼요.",
            RecordVisibilityOptions.values,
            state.value(SettingKeys.RECORD_VISIBILITY)
        ) {
            actions.setValue(SettingKeys.RECORD_VISIBILITY, it)
        }
        Divider()
        RunkkSwitchRow(
            title = "코스 지도 공유",
            description = "끄면 다른 러너와 기록 카드에 거리·시간만 보여요",
            checked = state.flag(SettingKeys.COURSE_MAP_SHARED),
            onCheckedChange = { actions.setValue(SettingKeys.COURSE_MAP_SHARED, it.toString()) }
        )
        Divider()
        ChoiceSetting(
            "개인정보 공개",
            "비공개면 나이대·성별·소개를 감춰요",
            SettingChoices.profileVisibility,
            state.value(SettingKeys.PROFILE_VISIBILITY)
        ) {
            actions.setValue(SettingKeys.PROFILE_VISIBILITY, it)
        }
    }
    Section("단위·기기") {
        ChoiceSetting("거리 단위", null, SettingChoices.unit, state.value(SettingKeys.UNIT)) {
            actions.setValue(SettingKeys.UNIT, it)
        }
        Divider()
        RunkkSwitchRow(
            title = "워치 연동",
            description = "워치에 러닝 정보를 보이고 심박을 받아요",
            checked = state.flag(SettingKeys.WATCH_LINKED),
            onCheckedChange = { on ->
                if (on &&
                    !state.consent(HEART_RATE)
                ) {
                    askHeartRate = true
                } else {
                    actions.setValue(SettingKeys.WATCH_LINKED, on.toString())
                }
            }
        )
    }
    Section("고지문서") {
        LegalRow("서비스 이용약관", LegalTypes.TERMS, actions)
        Divider()
        LegalRow("개인정보 처리방침", LegalTypes.PRIVACY_POLICY, actions)
        Divider()
        LegalRow("위치 정보 이용약관", LegalTypes.LOCATION_TERMS, actions)
        Divider()
        LegalRow("도움말", LegalTypes.HELP, actions)
    }
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

@Composable
private fun LegalRow(title: String, type: String, actions: SettingsActions) {
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
        RunkkTextLink("보기", onClick = { actions.onViewLegal(type, title) }, contentDescription = "$title 보기")
    }
}

@Composable
private fun ColumnScope.FreeRunSettings(state: SettingsUiState, actions: SettingsActions) {
    Section("자유런") {
        ChoiceSetting("목표 거리", "목표에 닿으면 한 번 알려 줘요", FreeRunTargets.values, state.value(SettingKeys.RUN_TARGET)) {
            actions.setValue(SettingKeys.RUN_TARGET, it)
        }
        Divider()
        RunkkSwitchRow("음성 안내", state.flag(SettingKeys.RUN_VOICE), {
            actions.setValue(SettingKeys.RUN_VOICE, it.toString())
        }, description = "1km마다 구간 기록을 읽어 줘요")
        Divider()
        RunkkSwitchRow("진동", state.flag(SettingKeys.RUN_VIBRATION), {
            actions.setValue(SettingKeys.RUN_VIBRATION, it.toString())
        })
        Divider()
        RunkkSwitchRow(
            "코치 계획 따르기",
            state.flag(SettingKeys.RUN_FOLLOW_COACH_PLAN),
            { actions.setValue(SettingKeys.RUN_FOLLOW_COACH_PLAN, it.toString()) },
            description = "오늘 계획의 목표 페이스보다 느리면 알려 줘요"
        )
    }
    Section("배틀런") {
        RunkkSwitchRow("음성 안내", state.flag(SettingKeys.BATTLE_VOICE), {
            actions.setValue(SettingKeys.BATTLE_VOICE, it.toString())
        }, description = "상대와의 거리 차를 읽어 줘요")
        Divider()
        RunkkSwitchRow("진동", state.flag(SettingKeys.BATTLE_VIBRATION), {
            actions.setValue(SettingKeys.BATTLE_VIBRATION, it.toString())
        })
        Divider()
        ChoiceSetting("중간 알림 간격", null, GapAlertIntervals.values, state.value(SettingKeys.BATTLE_GAP_ALERT)) {
            actions.setValue(SettingKeys.BATTLE_GAP_ALERT, it)
        }
    }
}

@Composable
private fun ColumnScope.NotificationSettings(state: SettingsUiState, actions: SettingsActions) {
    Text(
        "러닝 중 코칭 안내는 알림이 아니라 이 설정과 관계없어요.",
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
    Section("받을 알림") {
        NotificationTypes.values.forEachIndexed { index, (type, label) ->
            RunkkSwitchRow(label, state.flag(SettingKeys.alert(type)), {
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
    Section("필수 동의") {
        RequiredConsentRow("만 14세 이상 확인")
        Divider()
        RequiredConsentRow("서비스 이용약관", onView = { actions.onViewLegal(LegalTypes.TERMS, "서비스 이용약관") })
        Divider()
        RequiredConsentRow("개인정보 수집·이용", onView = { actions.onViewLegal(LegalTypes.PRIVACY_POLICY, "개인정보 수집·이용") })
    }
    Section("철회할 수 있는 동의") {
        RunkkSwitchRow(
            "위치 정보 수집",
            state.consent(LOCATION),
            { on -> if (on) actions.setConsent(LOCATION, true) else confirmWithdraw = LOCATION },
            description = "끄면 러닝을 측정할 수 없어요. 기록 조회·크루·아바타는 그대로 써요"
        )
        Divider()
        RunkkSwitchRow(
            "심박 수집",
            state.consent(HEART_RATE),
            { on -> if (on) askHeartRate = true else confirmWithdraw = HEART_RATE },
            description = "민감정보예요. 워치 심박을 코칭 안내 계산에만 써요"
        )
        Divider()
        RunkkSwitchRow("AI 코멘트용 집계값 외부 전송", state.consent(ConsentKeys.LLM_TRANSFER), {
            actions.setConsent(ConsentKeys.LLM_TRANSFER, it)
        }, description = "집계값만 국외 AI 서비스로 보내요. 끄면 규칙 기반 문구를 써요")
        Divider()
        RunkkSwitchRow("셀카 얼굴 변환 전송", state.consent(ConsentKeys.FACE_TRANSFER), {
            actions.setConsent(ConsentKeys.FACE_TRANSFER, it)
        }, description = "아바타 얼굴을 만들 때만 보내고 원본은 변환 직후 지워요")
        Divider()
        RunkkSwitchRow("마케팅 정보 수신", state.consent(ConsentKeys.MARKETING), {
            actions.setConsent(ConsentKeys.MARKETING, it)
        })
    }
    confirmWithdraw?.let { item ->
        RunkkConfirmDialog(
            title = if (item == LOCATION) "위치 수집 동의를 철회할까요?" else "심박 수집 동의를 철회할까요?",
            message = if (item == LOCATION) {
                "러닝 측정이 막히고, 저장된 위치 경로는 7일 안에 지워요. 거리·시간·페이스 요약은 남아요."
            } else {
                "심박 수집과 호흡 안내가 꺼지고, 저장된 심박 원자료를 지워요. 평균·최고 심박 요약은 남아요."
            },
            confirmLabel = "철회",
            onConfirm = {
                confirmWithdraw = null
                actions.setConsent(item, false)
            },
            onDismiss = { confirmWithdraw = null },
            destructive = true
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

@Composable
private fun RequiredConsentRow(title: String, onView: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RunkkTextStyles.text(15, FontWeight.Medium), color = RunkkColors.TextPrimary)
            Text("동의함 · 철회하려면 회원 탈퇴", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        if (onView != null) RunkkTextLink("보기", onClick = onView, contentDescription = "$title 보기")
    }
}

/** 심박 수집 동의(FR-014): 민감정보와 사용 목적을 먼저 보인다. */
@Composable
private fun HeartRateConsentDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = "심박 수집에 동의할까요?",
        message = "심박은 민감정보예요. 워치에서 받은 심박을 러닝 중 호흡 안내와 기록의 심박 구간 계산에만 써요. 언제든 동의 관리에서 철회할 수 있어요.",
        confirmLabel = "동의",
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
                loading = false,
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
            state = SettingsUiState(loading = false, settings = MemberSettings(emptyMap(), mapOf("LOCATION" to true))),
            actions = SettingsActions()
        )
    }
}

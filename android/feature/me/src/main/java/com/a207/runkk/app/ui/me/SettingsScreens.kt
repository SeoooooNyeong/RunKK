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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
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
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(page: SettingsPage, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == SettingsEvent.LoggedOut) navigator.restart(Routes.LOGIN) }
    }
    LaunchedEffect(state.urlToOpen) {
        state.urlToOpen?.let { url ->
            runCatching { uriHandler.openUri(url) }
                .onSuccess { viewModel.onUrlOpened() }
                .onFailure { viewModel.onUrlOpenFailed() }
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
            onMessageShown = viewModel::onMessageShown,
            // SDK 로그인은 이 화면의 코루틴에서 띄운다(화면이 사라지면 끊기고 ViewModel은 Activity를 잡지 않는다, A4).
            onLink = { provider ->
                viewModel.onLinkStart(provider)?.let { client ->
                    // UNDISPATCHED: 시작 전에 화면이 사라져도 try 안에서 취소를 받아 진행 표시를 푼다.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val token = try {
                            client.signIn(context)
                        } catch (e: CancellationException) {
                            viewModel.onLinkCancelled()
                            throw e
                        }
                        viewModel.onLinkToken(token)
                    }
                }
            },
            onNavigate = navigator::navigate,
            onLogout = viewModel::onLogoutConfirmed
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
            // 러닝 설정(A-5)은 피그마대로 제목을 가운데에 둔다.
            RunkkTopBar(
                title = stringResource(page.titleRes),
                onBack = actions.onBack,
                isTitleCentered = page == SettingsPage.FREE_RUN
            )
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
                        SettingsPage.CONSENTS -> ConsentSection(state, actions)
                        SettingsPage.ACCOUNT_LINK -> AccountLinkContent(state, actions)
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

/** 러닝 설정(A6): 자유런(목표 거리, 음성·진동 안내, 코치 계획 따르기)과 배틀런(기본 대결 거리, 안내, 근접 알림, 중간 알림 간격). */
@Composable
private fun FreeRunSettings(state: SettingsUiState, actions: SettingsActions) {
    Section(R.string.me_settings_section_free_run) {
        ChoiceSetting(
            R.string.me_settings_target_title,
            null,
            FreeRunTargets.values,
            state.value(SettingKeys.RUN_TARGET)
        ) {
            actions.setValue(SettingKeys.RUN_TARGET, it)
        }
        SettingsDivider()
        RunkkSwitchRow(stringResource(R.string.me_settings_voice_title), state.flag(SettingKeys.RUN_VOICE), {
            actions.setValue(SettingKeys.RUN_VOICE, it.toString())
        }, description = stringResource(R.string.me_settings_run_voice_description))
        SettingsDivider()
        RunkkSwitchRow(stringResource(R.string.me_settings_vibration_title), state.flag(SettingKeys.RUN_VIBRATION), {
            actions.setValue(SettingKeys.RUN_VIBRATION, it.toString())
        }, description = stringResource(R.string.me_settings_run_vibration_description))
        SettingsDivider()
        RunkkSwitchRow(
            stringResource(R.string.me_settings_follow_plan_title),
            state.flag(SettingKeys.RUN_FOLLOW_COACH_PLAN),
            { actions.setValue(SettingKeys.RUN_FOLLOW_COACH_PLAN, it.toString()) }
        )
    }
    Section(R.string.me_settings_section_battle) {
        // 기본 대결 거리(FR-109): 홈 배틀런 탭이 처음 고르는 거리. 값이 없으면 서버 기본값 5km.
        val battleDistance = state.value(BATTLE_DEFAULT_DISTANCE).takeIf { v -> BATTLE_DISTANCES.any { it.first == v } }
        ChoiceSetting(
            R.string.me_settings_battle_distance_title,
            null,
            BATTLE_DISTANCES,
            battleDistance ?: DEFAULT_BATTLE_DISTANCE
        ) {
            actions.setValue(BATTLE_DEFAULT_DISTANCE, it)
        }
        SettingsDivider()
        RunkkSwitchRow(stringResource(R.string.me_settings_voice_title), state.flag(SettingKeys.BATTLE_VOICE), {
            actions.setValue(SettingKeys.BATTLE_VOICE, it.toString())
        }, description = stringResource(R.string.me_settings_battle_voice_description))
        SettingsDivider()
        RunkkSwitchRow(stringResource(R.string.me_settings_vibration_title), state.flag(SettingKeys.BATTLE_VIBRATION), {
            actions.setValue(SettingKeys.BATTLE_VIBRATION, it.toString())
        }, description = stringResource(R.string.me_settings_battle_vibration_description))
        SettingsDivider()
        RunkkSwitchRow(
            stringResource(R.string.me_settings_proximity_title),
            state.flag(SettingKeys.BATTLE_PROXIMITY_ALERT),
            { actions.setValue(SettingKeys.BATTLE_PROXIMITY_ALERT, it.toString()) },
            description = stringResource(R.string.me_settings_proximity_description)
        )
        SettingsDivider()
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

/** 대결 설정의 기본 대결 거리 키(MemberSettings.BATTLE_DEFAULT_DISTANCE와 같은 서버 하위 항목). */
private const val BATTLE_DEFAULT_DISTANCE = "BATTLE_SETTINGS.defaultDistance"
private const val DEFAULT_BATTLE_DISTANCE = "KM5"

/** 랜덤 대결 거리(서버 StandardDistance RANDOM_BATTLE, FR-080). */
private val BATTLE_DISTANCES: List<Pair<String, Int>> = listOf(
    "KM1" to R.string.me_choice_battle_km1,
    "KM3" to R.string.me_choice_target_km3,
    "KM5" to R.string.me_choice_target_km5,
    "KM10" to R.string.me_choice_target_km10
)

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

@Preview(name = "F4 설정 (게스트)", widthDp = 390, heightDp = 1400)
@Composable
private fun AppSettingsPreview() {
    RunkkTheme(reduceMotion = true) {
        SettingsScreen(
            page = SettingsPage.APP,
            state = SettingsUiState(
                isLoading = false,
                isGuest = true,
                settings = MemberSettings(emptyMap(), mapOf("LOCATION" to true))
            ),
            actions = SettingsActions()
        )
    }
}

@Preview(name = "F9 소셜 계정 연동", widthDp = 390, heightDp = 844)
@Composable
private fun AccountLinkPreview() {
    RunkkTheme(reduceMotion = true) {
        SettingsScreen(
            page = SettingsPage.ACCOUNT_LINK,
            state = SettingsUiState(
                isLoading = false,
                isGuest = true,
                nickname = "러너1234",
                settings = MemberSettings(emptyMap(), emptyMap())
            ),
            actions = SettingsActions()
        )
    }
}

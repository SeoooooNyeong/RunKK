package com.a207.runkk.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCheckRow
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/**
 * 동의 화면 하나(가입·게스트 공용, FR-003·FR-004·FR-028·FR-197). [isGuest]면 로그인 화면의 "게스트로 시작"에서 온 것이고,
 * 아니면 소셜 로그인 결과 "가입필요"에서 온 것이다. 가입필요에서도 게스트로 시작할 수 있다(FR-001).
 */
@Composable
fun SignUpRoute(isGuest: Boolean, viewModel: SignUpViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignUpEvent.Completed -> navigator.restart(Routes.HOME)

                SignUpEvent.CompletedToLink -> {
                    navigator.restart(Routes.HOME)
                    navigator.navigate(MeRoutes.ACCOUNT_LINK)
                }

                SignUpEvent.Exit -> navigator.back()

                is SignUpEvent.OpenUrl ->
                    runCatching { uriHandler.openUri(event.url) }.onFailure { viewModel.onLegalOpenFailed() }
            }
        }
    }
    BackHandler(enabled = !state.isGuestNoticeVisible, onBack = viewModel::onExit)
    SignUpScreen(state = state, isGuest = isGuest, actions = viewModel.asActions())
}

private fun SignUpViewModel.asActions() = SignUpActions(
    onBack = ::onExit,
    onAllAgreedChange = ::onAllAgreedChange,
    onConsentChange = ::onConsentChange,
    onViewLegal = ::onViewLegal,
    onLegalDismissed = ::onLegalDismissed,
    onSubmit = ::onSubmit,
    onGuestNoticeConfirmed = ::onGuestNoticeConfirmed,
    onGuestNoticeLink = ::onGuestNoticeLinkTapped,
    onErrorShown = ::onErrorShown
)

@Composable
fun SignUpScreen(state: SignUpUiState, isGuest: Boolean, actions: SignUpActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.error?.asString()
    LaunchedEffect(state.error) {
        errorText?.let {
            snackbar.showSnackbar(it)
            actions.onErrorShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.me_signup_title),
                onBack = actions.onBack,
                isTitleCentered = true
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.submitError?.let { SubmitError(state, it.asString(), actions) }
                ConsentBody(state, actions)
            }
            BottomActions(state, isGuest, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
    state.legal?.let { LegalDialog(it, actions.onLegalDismissed) }
    if (state.isGuestNoticeVisible) {
        GuestNoticeDialog(
            nickname = state.guestNickname,
            onConfirm = actions.onGuestNoticeConfirmed,
            onLink = actions.onGuestNoticeLink
        )
    }
}

/** 거부·실패 안내. 가입 시간이 지났으면 "로그인으로 돌아가기"를 준다. */
@Composable
private fun SubmitError(state: SignUpUiState, message: String, actions: SignUpActions) {
    if (state.canRetrySubmit) {
        RunkkErrorState(message)
    } else {
        RunkkErrorState(
            message,
            onRetry = actions.onBack,
            retryLabel = stringResource(R.string.me_signup_back_to_login)
        )
    }
}

/** 제목, 전체 동의, 카드로 묶지 않은 목록: 필수(만 14세 확인 + 필수 3종), 구분선, 선택(선택 2종, 기본 꺼짐)(A-2, R2-07). */
@Composable
private fun ConsentBody(state: SignUpUiState, actions: SignUpActions) {
    Text(
        text = stringResource(R.string.me_signup_consent_headline),
        style = RunkkTextStyles.Headline,
        color = RunkkColors.TextPrimary,
        modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() }
    )
    RunkkCheckRow(
        label = stringResource(R.string.me_signup_all_agree),
        isChecked = state.isAllAgreed,
        onCheckedChange = actions.onAllAgreedChange,
        isHighlighted = true
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        SignUpConsents.items.filter { it.isRequired }.forEach { ConsentRow(it, state, actions) }
        HorizontalDivider(
            color = RunkkColors.Border,
            modifier = Modifier.padding(vertical = 6.dp).testTag(CONSENT_DIVIDER_TAG)
        )
        SignUpConsents.items.filterNot { it.isRequired }.forEach { ConsentRow(it, state, actions) }
    }
}

/** 동의 항목 한 줄(필수 또는 선택). */
@Composable
private fun ConsentRow(line: ConsentLine, state: SignUpUiState, actions: SignUpActions) {
    RunkkCheckRow(
        label = stringResource(line.labelRes),
        isChecked = state.consents[line.item] == true,
        onCheckedChange = { actions.onConsentChange(line.item, it) },
        tag = stringResource(
            if (line.isRequired) R.string.me_signup_tag_required else R.string.me_signup_tag_optional
        ),
        isTagHighlighted = line.isRequired,
        onView = line.legalType?.let { { actions.onViewLegal(line.item) } }
    )
    if (line.item == ConsentKeys.LLM_TRANSFER) {
        Text(
            text = stringResource(R.string.me_consents_llm_message),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** 필수와 선택 사이 구분선의 시험 표지. */
internal const val CONSENT_DIVIDER_TAG = "signup-consent-divider"

/** 아래 버튼 하나: "시작하기". 소셜 로그인에서 왔으면 가입, 게스트로 시작에서 왔으면 게스트로 시작한다. */
@Composable
private fun BottomActions(state: SignUpUiState, isGuest: Boolean, actions: SignUpActions) {
    RunkkPrimaryButton(
        text = stringResource(R.string.me_signup_submit),
        onClick = { actions.onSubmit(isGuest) },
        isEnabled = state.canSubmit || state.isSubmitting,
        isLoading = state.isSubmitting,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
    )
}

@Preview(name = "A2 가입 동의", widthDp = 390, heightDp = 844)
@Composable
private fun SignUpConsentPreview() {
    RunkkTheme(reduceMotion = true) {
        SignUpScreen(
            state = SignUpUiState(consents = SignUpConsents.items.associate { it.item to it.isRequired }),
            isGuest = false,
            actions = SignUpActions()
        )
    }
}

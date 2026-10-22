package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkLogo
import com.a207.runkk.app.core.designsystem.RunkkLogoKind
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.ProviderType

/** 로그인(A1, FR-001): 카카오·구글. 가입된 회원은 홈, 아니면 단계별 가입으로 간다. */
@Composable
fun LoginRoute(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.LoggedIn -> navigator.restart(Routes.HOME)
                LoginEvent.SignUpRequired -> navigator.navigate(Routes.SIGN_UP)
            }
        }
    }
    LoginScreen(
        state = state,
        onProvider = { viewModel.onProviderTapped(it, context) },
        onErrorShown = viewModel::onErrorShown
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onProvider: (ProviderType) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.error?.asString()
    LaunchedEffect(state.error) {
        errorText?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Text(
            text = "RUN",
            style = RunkkTextStyles.display(200),
            color = RunkkColors.BackgroundDecor,
            modifier = Modifier.offset(x = (-24).dp, y = 120.dp).clearAndSetSemantics {}
        )
        Column(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
            LoginIntro(modifier = Modifier.weight(1f).fillMaxWidth())
            LoginButtons(state, onProvider)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

/** 로고와 소개 문구. */
@Composable
private fun LoginIntro(modifier: Modifier = Modifier) {
    val highlight = stringResource(R.string.me_login_tagline_highlight)
    val tagline = stringResource(R.string.me_login_tagline, highlight)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
        RunkkLogo(RunkkLogoKind.WordmarkKo, width = 240.dp, modifier = Modifier.semantics { heading() })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(tagline)
                    val start = tagline.indexOf(highlight)
                    if (start >= 0) {
                        addStyle(SpanStyle(color = RunkkColors.Lime), start, start + highlight.length)
                    }
                },
                style = RunkkTextStyles.text(18, FontWeight.Bold, lineHeight = 28),
                color = RunkkColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.me_login_subtitle),
                style = RunkkTextStyles.text(14, lineHeight = 24),
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 카카오·구글 시작 버튼. */
@Composable
private fun LoginButtons(state: LoginUiState, onProvider: (ProviderType) -> Unit) {
    val googleLabel =
        if (state.inProgress == ProviderType.GOOGLE) R.string.me_login_google_in_progress else R.string.me_login_google
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RunkkPrimaryButton(
            text = stringResource(R.string.me_login_kakao),
            onClick = { onProvider(ProviderType.KAKAO) },
            isLoading = state.inProgress == ProviderType.KAKAO,
            isEnabled = state.inProgress == null || state.inProgress == ProviderType.KAKAO,
            modifier = Modifier.fillMaxWidth()
        )
        RunkkSecondaryButton(
            text = stringResource(googleLabel),
            onClick = { onProvider(ProviderType.GOOGLE) },
            isEnabled = state.inProgress == null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "A1 로그인", widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    RunkkTheme(reduceMotion = true) { LoginScreen(state = LoginUiState(), onProvider = {}, onErrorShown = {}) }
}

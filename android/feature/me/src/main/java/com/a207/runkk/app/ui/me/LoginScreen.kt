package com.a207.runkk.app.ui.me

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBackgroundMusic
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.ProviderType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** 로그인(A1, FR-001): 카카오·구글·게스트. 가입된 회원은 홈, 아니면 동의 화면으로 간다. */
@Composable
fun LoginRoute(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
        // SDK 로그인은 이 화면의 코루틴에서 띄운다(화면이 사라지면 끊기고 ViewModel은 Activity를 잡지 않는다, AN5-D-11).
        onProvider = { provider ->
            viewModel.onLoginStart(provider)?.let { client ->
                // UNDISPATCHED: 시작 전에 화면이 사라져도 try 안에서 취소를 받아 진행 표시를 푼다.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val token = try {
                        client.signIn(context)
                    } catch (e: CancellationException) {
                        viewModel.onLoginCancelled()
                        throw e
                    }
                    viewModel.onLoginToken(token)
                }
            }
        },
        onGuest = { navigator.navigate(Routes.GUEST_START) },
        onErrorShown = viewModel::onErrorShown
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onProvider: (ProviderType) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    onGuest: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.error?.asString()
    LaunchedEffect(state.error) {
        errorText?.let {
            snackbar.showSnackbar(it)
            onErrorShown()
        }
    }
    // 로그인 배경 음악(사용자 요청 2026-10-31). 원본 음량 그대로 튼다
    RunkkBackgroundMusic(R.raw.login_bgm)
    // 피그마 A-1: 배경 영상 위에 버튼만 둔다(로고와 소개 문구 없음)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        LoginVideoBackground(R.raw.login_background, R.drawable.login_video_poster, Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            LoginButtons(state, onProvider, onGuest)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

/** 카카오·구글 시작 버튼(피그마의 브랜드 버튼 그림), "또는" 구분선, "게스트로 시작하기"(FR-001, FR-197). */
@Composable
private fun LoginButtons(state: LoginUiState, onProvider: (ProviderType) -> Unit, onGuest: () -> Unit) {
    val googleLabel =
        if (state.inProgress == ProviderType.GOOGLE) R.string.me_login_google_in_progress else R.string.me_login_google
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandLoginButton(
            image = R.drawable.login_button_kakao,
            label = stringResource(R.string.me_login_kakao),
            isLoading = state.inProgress == ProviderType.KAKAO,
            isEnabled = state.inProgress == null,
            onClick = { onProvider(ProviderType.KAKAO) }
        )
        BrandLoginButton(
            image = R.drawable.login_button_google,
            label = stringResource(googleLabel),
            isLoading = state.inProgress == ProviderType.GOOGLE,
            isEnabled = state.inProgress == null,
            onClick = { onProvider(ProviderType.GOOGLE) }
        )
        OrDivider()
        RunkkSecondaryButton(
            text = stringResource(R.string.me_login_guest),
            onClick = onGuest,
            isEnabled = state.inProgress == null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 카카오·구글 브랜드 버튼(피그마 "Social login · Photo reference": 원본 비율, 로고, 문구, 색을 그대로 둔 그림). 문구가 그림 안에
 * 있으므로 읽기 이름은 [label]이다. 진행 중이면 오른쪽에 진행 표시를 겹친다.
 */
@Composable
private fun BrandLoginButton(
    @DrawableRes image: Int,
    label: String,
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BRAND_BUTTON_RATIO)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .alpha(if (isEnabled || isLoading) 1f else DISABLED_ALPHA)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterEnd
    ) {
        Image(
            painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 16.dp).size(20.dp),
                color = Color.Black,
                strokeWidth = 2.dp
            )
        }
    }
}

/** 피그마 브랜드 버튼 크기 342×55.13. */
private const val BRAND_BUTTON_RATIO = 342f / 55.128f

private const val DISABLED_ALPHA = 0.5f

/** 소셜 로그인과 게스트 시작 사이 "또는" 구분선. */
@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = RunkkColors.Border)
        Text(stringResource(R.string.me_login_or), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        HorizontalDivider(modifier = Modifier.weight(1f), color = RunkkColors.Border)
    }
}

@Preview(name = "A1 로그인", widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    RunkkTheme(reduceMotion = true) { LoginScreen(state = LoginUiState(), onProvider = {}, onErrorShown = {}) }
}

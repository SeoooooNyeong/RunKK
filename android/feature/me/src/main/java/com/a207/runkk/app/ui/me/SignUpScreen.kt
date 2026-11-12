package com.a207.runkk.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunkkCheckRow
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.defaultAvatarOf
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

/**
 * 가입·게스트 공용 화면(FR-003·FR-004·FR-028·FR-197): 동의 → 성별 고르기(S15P31A207-300) 두 단계. [isGuest]면 로그인 화면의
 * "게스트로 시작"에서 온 것이고, 아니면 소셜 로그인 결과 "가입필요"에서 온 것이다. 가입필요에서도 게스트로 시작할 수 있다(FR-001).
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
    BackHandler(enabled = !state.isGuestNoticeVisible, onBack = viewModel::onBack)
    SignUpScreen(state = state, isGuest = isGuest, actions = viewModel.asActions())
}

private fun SignUpViewModel.asActions() = SignUpActions(
    onBack = ::onBack,
    onExit = ::onExit,
    onAllAgreedChange = ::onAllAgreedChange,
    onConsentChange = ::onConsentChange,
    onViewLegal = ::onViewLegal,
    onLegalDismissed = ::onLegalDismissed,
    onConsentNext = ::onConsentNext,
    onGenderSelect = ::onGenderSelect,
    onStart = ::onStart,
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
            val isGenderStep = state.step == SignUpStep.GENDER
            RunkkTopBar(
                title = stringResource(R.string.me_signup_title),
                // 계정을 만든 뒤(성별 저장만 남음)에는 동의로 돌아가지 않는다
                onBack = actions.onBack.takeIf { !isGenderStep || state.canGoBackToConsent },
                isTitleCentered = true,
                actions = { if (isGenderStep) StepCounter(current = GENDER_STEP, total = GENDER_STEP) }
            )
            // 단계마다 스크롤을 따로 둔다(동의를 내려 본 채로 성별 단계가 중간부터 보이지 않게)
            val scroll = remember(state.step) { ScrollState(initial = 0) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.submitError?.let { SubmitError(state, it.asString(), actions) }
                if (isGenderStep) GenderBody(state, actions) else ConsentBody(state, actions)
            }
            if (isGenderStep) GenderBottomAction(state, isGuest, actions) else BottomActions(state, actions)
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
            onRetry = actions.onExit,
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

/** 동의 단계 아래 버튼 하나: "시작하기". 필수 동의를 마치면 성별 단계로 간다(아직 보내지 않는다, S15P31A207-300). */
@Composable
private fun BottomActions(state: SignUpUiState, actions: SignUpActions) {
    RunkkPrimaryButton(
        text = stringResource(R.string.me_signup_submit),
        onClick = actions.onConsentNext,
        isEnabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth().padding(BOTTOM_BUTTON_PADDING)
    )
}

/** 위 바 오른쪽의 단계 표시("2 / 2", 12 보조색). 보조 기술은 "2단계 중 2단계"로 읽는다. */
@Composable
private fun StepCounter(current: Int, total: Int) {
    val spoken = stringResource(R.string.me_signup_step_description, current, total)
    Text(
        text = stringResource(R.string.me_signup_step, current, total),
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary,
        modifier = Modifier.padding(end = 16.dp).semantics { contentDescription = spoken }
    )
}

/**
 * 성별 고르기 단계(S15P31A207-300, 피그마 "가입 · 성별 고르기 단계 · 제안"): 제목, 설명, 남자·여자 카드 둘(나란히, 처음에는 고르지 않음).
 * 카드에는 그 성별의 기본 전신 아바타를 세운다.
 */
@Composable
private fun GenderBody(state: SignUpUiState, actions: SignUpActions) {
    Column(modifier = Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.me_signup_gender_headline),
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.me_signup_gender_body),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GENDER_CHOICES.forEach { gender ->
            GenderCard(
                gender = gender,
                isSelected = state.gender == gender,
                isEnabled = !state.isSubmitting,
                onSelect = { actions.onGenderSelect(gender) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 성별 카드 하나(피그마 152×300, 모서리 20, 흰 바탕·1 테두리). 고르면 2 보라 테두리, 보라 옅은 바탕, 오른쪽 위 체크, 보라 글자다.
 * 그림은 전신 정지 그림 칸(꾸미기가 열려 있으면 그 성별의 기본 모습)이고, 칸이 없으면 성별 기본 서기 그림이다.
 */
@Composable
private fun GenderCard(
    gender: Gender,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GENDER_CARD_RADIUS)
    Box(
        modifier = modifier
            .height(GENDER_CARD_HEIGHT)
            .clip(shape)
            .background(if (isSelected) RunkkColors.SecondaryContainer else RunkkColors.Surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) RunkkColors.Violet else RunkkColors.Border,
                shape = shape
            )
            .selectable(selected = isSelected, enabled = isEnabled, role = Role.RadioButton, onClick = onSelect)
            .testTag(GENDER_CARD_TAG + gender.name)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val figure = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
            AvatarStillSlot(AvatarStillSubject(isMine = false, isFemale = gender == Gender.FEMALE), figure) {
                Image(
                    painterResource(defaultAvatarOf(AvatarExpression.STAND, AvatarCharacter.of(gender))),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    modifier = figure
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (gender == Gender.FEMALE) R.string.me_signup_gender_female else R.string.me_signup_gender_male
                ),
                style = RunkkTextStyles.CardTitle,
                // 라이트는 보라(color/secondary), 다크는 옅은 보라 바탕 위에서 읽히는 연보라다
                color = if (isSelected) RunkkColors.Lavender else RunkkColors.TextPrimary
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(CHECK_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(RunkkColors.Violet)
                    .testTag(GENDER_CHECK_TAG),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    RunkkIcons.Check,
                    contentDescription = null,
                    tint = RunkkColors.OnSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 성별 단계 아래 버튼: "시작하기". 고르기 전에는 꺼져 있고, 누르면 가입(게스트 시작) 뒤 성별을 저장하고 홈으로 간다. */
@Composable
private fun GenderBottomAction(state: SignUpUiState, isGuest: Boolean, actions: SignUpActions) {
    RunkkPrimaryButton(
        text = stringResource(R.string.me_signup_submit),
        onClick = { actions.onStart(isGuest) },
        isEnabled = state.canStart || state.isSubmitting,
        isLoading = state.isSubmitting,
        modifier = Modifier.fillMaxWidth().padding(BOTTOM_BUTTON_PADDING)
    )
}

/** 성별 카드의 시험 표지(뒤에 Gender 이름). */
internal const val GENDER_CARD_TAG = "signup-gender-card-"

/** 고른 성별 카드의 체크 표지. */
internal const val GENDER_CHECK_TAG = "signup-gender-check"

/** 성별 단계에서 고를 수 있는 성별(왼쪽부터). */
private val GENDER_CHOICES = listOf(Gender.MALE, Gender.FEMALE)

/** 성별 단계는 두 단계 중 둘째다. */
private const val GENDER_STEP = 2

private val GENDER_CARD_HEIGHT: Dp = 300.dp
private val GENDER_CARD_RADIUS: Dp = 20.dp
private val CHECK_BADGE_SIZE: Dp = 24.dp
private val BOTTOM_BUTTON_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)

@Preview(name = "A2 가입 성별 고르기", widthDp = 360, heightDp = 800)
@Composable
private fun SignUpGenderPreview() {
    RunkkTheme(reduceMotion = true) {
        SignUpScreen(
            state = SignUpUiState(
                step = SignUpStep.GENDER,
                consents = SignUpConsents.items.associate { it.item to it.isRequired },
                gender = Gender.MALE
            ),
            isGuest = false,
            actions = SignUpActions()
        )
    }
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

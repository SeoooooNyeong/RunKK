package com.a207.runkk.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkCheckRow
import com.a207.runkk.app.core.designsystem.RunkkChipGroup
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStepTopBar
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.runkkDashedBorder
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.Gender
import java.time.Year

/** 단계별 가입(A2~A4 + 아바타 단계, FR-003·FR-004·FR-028). */
@Composable
fun SignUpRoute(viewModel: SignUpViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SignUpEvent.Completed -> {
                    navigator.restart(Routes.HOME)
                    if (event.shouldCreateAvatar) navigator.navigate(Routes.AVATAR_SETUP)
                }

                SignUpEvent.Exit -> navigator.back()

                is SignUpEvent.OpenUrl -> runCatching { uriHandler.openUri(event.url) }
            }
        }
    }
    BackHandler(onBack = viewModel::onBack)
    SignUpScreen(state = state, actions = viewModel.asActions())
}

/** 가입 화면의 사용자 행동 묶음(상태 없는 화면이 ViewModel을 모르게). */
data class SignUpActions(
    val onBack: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onAllAgreedChange: (Boolean) -> Unit = {},
    val onConsentChange: (String, Boolean) -> Unit = { _, _ -> },
    val onViewLegal: (String) -> Unit = {},
    val onLegalDismissed: () -> Unit = {},
    val onNicknameChange: (String) -> Unit = {},
    val onBirthYearChange: (Int?) -> Unit = {},
    val onGenderChange: (Gender) -> Unit = {},
    val onIntroChange: (String) -> Unit = {},
    val onVisibilityChange: (String) -> Unit = {},
    val onTagToggled: (String) -> Unit = {},
    val onSubmit: (Boolean) -> Unit = {},
    val onRetrySubmit: () -> Unit = {},
    val onExit: () -> Unit = {},
    val onErrorShown: () -> Unit = {}
)

private fun SignUpViewModel.asActions() = SignUpActions(
    onBack = ::onBack,
    onNext = ::onNext,
    onAllAgreedChange = ::onAllAgreedChange,
    onConsentChange = ::onConsentChange,
    onViewLegal = ::onViewLegal,
    onLegalDismissed = ::onLegalDismissed,
    onNicknameChange = ::onNicknameChange,
    onBirthYearChange = ::onBirthYearChange,
    onGenderChange = ::onGenderChange,
    onIntroChange = ::onIntroChange,
    onVisibilityChange = ::onVisibilityChange,
    onTagToggled = ::onTagToggled,
    onSubmit = ::onSubmit,
    onRetrySubmit = ::onRetrySubmit,
    onExit = ::onExit,
    onErrorShown = ::onErrorShown
)

@Composable
fun SignUpScreen(state: SignUpUiState, actions: SignUpActions, modifier: Modifier = Modifier) {
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
            RunkkStepTopBar(
                title = stringResource(R.string.me_signup_title),
                step = state.step.number,
                total = SignUpStep.TOTAL,
                onBack = actions.onBack
            )
            SignUpBody(state, actions, modifier = Modifier.weight(1f))
            BottomActions(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
    state.legal?.let { LegalDialog(it, actions.onLegalDismissed) }
}

/** 가입 거부 안내와 현재 단계 내용(스크롤). */
@Composable
private fun SignUpBody(state: SignUpUiState, actions: SignUpActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (state.step == SignUpStep.TAGS) 24.dp else 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.submitError?.let { SubmitError(state, it.asString(), actions) }
        when (state.step) {
            SignUpStep.CONSENT -> ConsentStep(state, actions)
            SignUpStep.PROFILE -> ProfileStep(state, actions)
            SignUpStep.TAGS -> TagsStep(state, actions)
            SignUpStep.AVATAR -> AvatarStep()
        }
    }
}

/** 가입 요청이 거부·실패한 안내. 5단계에서는 "다시 시도", 가입 시간이 지났으면 "로그인으로 돌아가기"를 준다. */
@Composable
private fun SubmitError(state: SignUpUiState, message: String, actions: SignUpActions) {
    when {
        !state.canRetrySubmit -> RunkkErrorState(
            message,
            onRetry = actions.onExit,
            retryLabel = stringResource(R.string.me_signup_back_to_login)
        )

        state.step == SignUpStep.AVATAR && !state.isSubmitting ->
            RunkkErrorState(message, onRetry = actions.onRetrySubmit)

        else -> RunkkErrorState(message)
    }
}

@Composable
private fun StepHeadline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = RunkkTextStyles.Headline,
        color = RunkkColors.TextPrimary,
        modifier = modifier.semantics {
            heading()
        }
    )
}

@Composable
private fun ColumnScope.ConsentStep(state: SignUpUiState, actions: SignUpActions) {
    StepHeadline(stringResource(R.string.me_signup_consent_headline), modifier = Modifier.padding(horizontal = 8.dp))
    RunkkCheckRow(
        label = stringResource(R.string.me_signup_all_agree),
        isChecked = state.isAllAgreed,
        onCheckedChange = actions.onAllAgreedChange,
        isHighlighted = true
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RunkkColors.Surface, RunkkShapes.Medium)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Medium)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SignUpConsents.items.forEachIndexed { index, line ->
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
            if (index < SignUpConsents.items.lastIndex) HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

@Composable
private fun ColumnScope.ProfileStep(state: SignUpUiState, actions: SignUpActions) {
    StepHeadline(stringResource(R.string.me_signup_profile_headline), modifier = Modifier.padding(horizontal = 8.dp))
    val nicknameError = state.nickname.isNotBlank() && state.nickname.trim().length !in SignUpUiState.NICKNAME_LENGTH
    RunkkTextField(
        value = state.nickname,
        onValueChange = actions.onNicknameChange,
        label = stringResource(R.string.me_signup_nickname_label),
        placeholder = stringResource(R.string.me_profile_nickname_placeholder),
        maxLength = SignUpUiState.NICKNAME_LENGTH.last,
        error = if (nicknameError) stringResource(R.string.me_signup_nickname_error) else null
    )
    BirthGenderRow(state, actions)
    RunkkTextField(
        value = state.intro,
        onValueChange = actions.onIntroChange,
        label = stringResource(R.string.me_profile_intro_label),
        placeholder = stringResource(R.string.me_profile_intro_placeholder),
        maxLength = SignUpUiState.INTRO_MAX,
        imeAction = androidx.compose.ui.text.input.ImeAction.Done
    )
    VisibilityCard(state, actions)
}

/** 출생연도·성별 선택칸. */
@Composable
private fun BirthGenderRow(state: SignUpUiState, actions: SignUpActions) {
    val resources = LocalContext.current.resources
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val thisYear = remember { Year.now().value }
        RunkkSelectField(
            label = stringResource(R.string.me_signup_birth_year_label),
            options = remember(thisYear) { (thisYear downTo OLDEST_BIRTH_YEAR).toList() },
            selected = state.birthYear,
            optionLabel = { resources.getString(R.string.me_signup_birth_year_option, it) },
            onSelect = { actions.onBirthYearChange(it) },
            modifier = Modifier.weight(1f)
        )
        RunkkSelectField(
            label = stringResource(R.string.me_signup_gender_label),
            options = Gender.entries,
            selected = state.gender,
            optionLabel = { resources.getString(genderLabelRes(it)) },
            onSelect = actions.onGenderChange,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 기록 공개 범위 선택 카드(3단계 안내). */
@Composable
private fun VisibilityCard(state: SignUpUiState, actions: SignUpActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RunkkColors.Surface, RunkkShapes.Medium)
            .runkkDashedBorder()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "NEXT · 3 / 5",
            style = RunkkTextStyles.display(14),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Text(
            stringResource(R.string.me_signup_visibility_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics {
                heading()
            }
        )
        val options = RecordVisibilityOptions.values
        RunkkSegmentRow(
            options = options.map { stringResource(it.second) },
            selectedIndex = options.indexOfFirst { it.first == state.recordVisibility },
            onSelect = { actions.onVisibilityChange(options[it].first) }
        )
    }
}

@Composable
private fun ColumnScope.TagsStep(state: SignUpUiState, actions: SignUpActions) {
    StepHeadline(stringResource(R.string.me_signup_tags_headline))
    RunkkTags.runnerCategories.forEach { category ->
        RunkkChipGroup(title = stringResource(category.nameRes)) {
            category.tags.forEach { tag ->
                RunkkChoiceChip(
                    label = stringResource(tag.labelRes),
                    isSelected = tag.id in state.tags,
                    onSelectedChange = { actions.onTagToggled(tag.id) }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AvatarStep() {
    StepHeadline(stringResource(R.string.me_signup_avatar_headline), modifier = Modifier.padding(horizontal = 8.dp))
    RunkkCard(shape = RunkkShapes.ExtraLarge, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 96.dp, height = 120.dp)
                .runkkDashedBorder(corner = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.Camera,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = stringResource(R.string.me_signup_avatar_description),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BottomActions(state: SignUpUiState, actions: SignUpActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = 16.dp,
            end = 16.dp,
            top = if (state.step ==
                SignUpStep.CONSENT
            ) {
                16.dp
            } else {
                0.dp
            },
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (state.step) {
            SignUpStep.AVATAR -> {
                RunkkPrimaryButton(
                    text = stringResource(R.string.me_signup_submit_with_avatar),
                    onClick = { actions.onSubmit(true) },
                    isLoading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                RunkkSecondaryButton(text = stringResource(R.string.me_signup_submit_later), onClick = {
                    actions.onSubmit(false)
                }, isEnabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth())
            }

            else -> {
                if (state.step == SignUpStep.TAGS) TagsSelectedCount(state.tags.size)
                RunkkPrimaryButton(
                    text = stringResource(R.string.me_signup_next),
                    onClick = actions.onNext,
                    isEnabled = state.canProceed,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** "N개 선택" 줄. 숫자만 라임색 굵은 글씨. */
@Composable
private fun TagsSelectedCount(count: Int) {
    val number = "$count"
    val text = stringResource(R.string.me_signup_tags_selected, count)
    Text(
        text = buildAnnotatedString {
            append(text)
            val start = text.indexOf(number)
            if (start >= 0) {
                addStyle(
                    SpanStyle(color = RunkkColors.Lime, fontWeight = FontWeight.Bold),
                    start,
                    start + number.length
                )
            }
        },
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@StringRes
internal fun genderLabelRes(gender: Gender): Int = when (gender) {
    Gender.FEMALE -> R.string.me_gender_female
    Gender.MALE -> R.string.me_gender_male
    Gender.UNDISCLOSED -> R.string.me_gender_undisclosed
}

private const val OLDEST_BIRTH_YEAR = 1930

@Preview(name = "A2 가입 1/5 약관·동의", widthDp = 390, heightDp = 844)
@Composable
private fun SignUpConsentPreview() {
    RunkkTheme(reduceMotion = true) {
        SignUpScreen(
            state = SignUpUiState(
                consents = SignUpConsents.items.associate {
                    it.item to true
                }
            ),
            actions = SignUpActions()
        )
    }
}

@Preview(name = "A3 가입 2·3/5 프로필·공개 범위", widthDp = 390, heightDp = 844)
@Composable
private fun SignUpProfilePreview() {
    RunkkTheme(reduceMotion = true) {
        SignUpScreen(state = SignUpUiState(step = SignUpStep.PROFILE), actions = SignUpActions())
    }
}

@Preview(name = "A4 가입 4/5 성향 태그", widthDp = 390, heightDp = 844)
@Composable
private fun SignUpTagsPreview() {
    RunkkTheme(reduceMotion = true) {
        SignUpScreen(
            state = SignUpUiState(step = SignUpStep.TAGS, tags = setOf("SOCIAL_RUN", "FAMILIAR_ROUTE", "EVENING")),
            actions = SignUpActions()
        )
    }
}

@Preview(name = "가입 5/5 아바타", widthDp = 390, heightDp = 844)
@Composable
private fun SignUpAvatarPreview() {
    RunkkTheme(reduceMotion = true) {
        SignUpScreen(state = SignUpUiState(step = SignUpStep.AVATAR), actions = SignUpActions())
    }
}

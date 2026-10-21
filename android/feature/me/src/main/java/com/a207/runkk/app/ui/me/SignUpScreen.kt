package com.a207.runkk.app.ui.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkCheckRow
import com.a207.runkk.app.core.designsystem.RunkkChipGroup
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
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
                    if (event.createAvatar) navigator.navigate(Routes.AVATAR_SETUP)
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
    onErrorShown = ::onErrorShown
)

@Composable
fun SignUpScreen(state: SignUpUiState, actions: SignUpActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            actions.onErrorShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkStepTopBar(
                title = "회원 가입",
                step = state.step.number,
                total = SignUpStep.TOTAL,
                onBack = actions.onBack
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (state.step == SignUpStep.TAGS) 24.dp else 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state.step) {
                    SignUpStep.CONSENT -> ConsentStep(state, actions)
                    SignUpStep.PROFILE -> ProfileStep(state, actions)
                    SignUpStep.TAGS -> TagsStep(state, actions)
                    SignUpStep.AVATAR -> AvatarStep()
                }
            }
            BottomActions(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
    state.legal?.let { legal ->
        AlertDialog(
            onDismissRequest = actions.onLegalDismissed,
            containerColor = RunkkColors.Surface,
            title = { Text(legal.title, style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary) },
            text = {
                Text(
                    text = legal.body,
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
    StepHeadline("달리기 전,\n약속 하나만 해요", modifier = Modifier.padding(horizontal = 8.dp))
    RunkkCheckRow(
        label = "전체 동의",
        checked = state.allAgreed,
        onCheckedChange = actions.onAllAgreedChange,
        highlighted = true
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
                label = line.label,
                checked = state.consents[line.item] == true,
                onCheckedChange = { actions.onConsentChange(line.item, it) },
                tag = if (line.required) "[필수]" else "[선택]",
                tagHighlighted = line.required,
                onView = line.legalType?.let { { actions.onViewLegal(line.item) } }
            )
            if (index < SignUpConsents.items.lastIndex) HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

@Composable
private fun ColumnScope.ProfileStep(state: SignUpUiState, actions: SignUpActions) {
    StepHeadline("트랙에서 사용할\n이름을 정해요", modifier = Modifier.padding(horizontal = 8.dp))
    val nicknameError = state.nickname.isNotBlank() && state.nickname.trim().length !in SignUpUiState.NICKNAME_LENGTH
    RunkkTextField(
        value = state.nickname,
        onValueChange = actions.onNicknameChange,
        label = "닉네임 · 2~20자, 비우면 계정 이름",
        placeholder = "닉네임",
        maxLength = SignUpUiState.NICKNAME_LENGTH.last,
        error = if (nicknameError) "닉네임은 2~20자로 입력해 주세요" else null
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val thisYear = remember { Year.now().value }
        RunkkSelectField(
            label = "출생연도 (선택)",
            options = remember(thisYear) { (thisYear downTo OLDEST_BIRTH_YEAR).toList() },
            selected = state.birthYear,
            optionLabel = { "${it}년" },
            onSelect = { actions.onBirthYearChange(it) },
            modifier = Modifier.weight(1f)
        )
        RunkkSelectField(
            label = "성별 (선택)",
            options = Gender.entries,
            selected = state.gender,
            optionLabel = ::genderLabel,
            onSelect = actions.onGenderChange,
            modifier = Modifier.weight(1f)
        )
    }
    RunkkTextField(
        value = state.intro,
        onValueChange = actions.onIntroChange,
        label = "한 줄 소개 (선택, 40자)",
        placeholder = "소개",
        maxLength = SignUpUiState.INTRO_MAX,
        imeAction = androidx.compose.ui.text.input.ImeAction.Done
    )
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
            "내 기록을 누구에게 보여 줄까요?",
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics {
                heading()
            }
        )
        val options = RecordVisibilityOptions.values
        RunkkSegmentRow(
            options = options.map { it.second },
            selectedIndex = options.indexOfFirst { it.first == state.recordVisibility },
            onSelect = { actions.onVisibilityChange(options[it].first) }
        )
    }
}

@Composable
private fun ColumnScope.TagsStep(state: SignUpUiState, actions: SignUpActions) {
    StepHeadline("나는 어떤 러너일까요?")
    RunkkTags.runnerCategories.forEach { category ->
        RunkkChipGroup(title = category.name) {
            category.tags.forEach { tag ->
                RunkkChoiceChip(label = tag.label, selected = tag.id in state.tags, onSelectedChange = {
                    actions.onTagToggled(tag.id)
                })
            }
        }
    }
}

@Composable
private fun ColumnScope.AvatarStep() {
    StepHeadline("마지막으로,\n나만의 아바타를 만들어요", modifier = Modifier.padding(horizontal = 8.dp))
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
            text = "셀카로 SD 얼굴을 만들거나 기본 캐릭터 4종에서 고를 수 있어요.\n셀카 원본은 변환한 뒤 바로 지워요.",
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
                    text = "가입하고 아바타 만들기",
                    onClick = { actions.onSubmit(true) },
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                RunkkSecondaryButton(text = "나중에 만들기", onClick = {
                    actions.onSubmit(false)
                }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth())
            }

            else -> {
                if (state.step == SignUpStep.TAGS) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = RunkkColors.Lime, fontWeight = FontWeight.Bold)) {
                                append("${state.tags.size}")
                            }
                            append("개 선택")
                        },
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RunkkPrimaryButton(
                    text = "다음",
                    onClick = actions.onNext,
                    enabled = state.canProceed,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

internal fun genderLabel(gender: Gender): String = when (gender) {
    Gender.FEMALE -> "여성"
    Gender.MALE -> "남성"
    Gender.UNDISCLOSED -> "밝히지 않음"
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
            state = SignUpUiState(step = SignUpStep.TAGS, tags = setOf("SOCIAL", "FAMILIAR_ROUTE", "EVENING")),
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

package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkSpinner
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.SelectLabel
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import java.time.Year

/** 프로필 편집(F3, FR-007). */
@Composable
fun ProfileEditRoute(viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) { viewModel.events.collect { navigator.back() } }
    ProfileEditScreen(
        state = state,
        onBack = navigator::back,
        onNicknameChange = viewModel::onNicknameChange,
        onIntroChange = viewModel::onIntroChange,
        onSave = viewModel::onSave,
        onErrorShown = viewModel::onErrorShown,
        onAgePublicChange = viewModel::onAgePublicChange,
        onGenderPublicChange = viewModel::onGenderPublicChange,
        onGenderChange = viewModel::onGenderChange,
        onBirthYearChange = viewModel::onBirthYearChange
    )
}

@Composable
fun ProfileEditScreen(
    state: ProfileEditUiState,
    onBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onSave: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    onAgePublicChange: (Boolean) -> Unit = {},
    onGenderPublicChange: (Boolean) -> Unit = {},
    onGenderChange: (Gender) -> Unit = {},
    onBirthYearChange: (Int?) -> Unit = {}
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
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.me_profile_edit_title),
                onBack = onBack,
                isTitleCentered = true
            ) {
                SaveAction(state, onSave)
            }
            if (state.isLoading) {
                RunkkLoading(modifier = Modifier.weight(1f))
            } else {
                ProfileEditForm(
                    state = state,
                    onNicknameChange = onNicknameChange,
                    onIntroChange = onIntroChange,
                    onAgePublicChange = onAgePublicChange,
                    onGenderPublicChange = onGenderPublicChange,
                    onGenderChange = onGenderChange,
                    onBirthYearChange = onBirthYearChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}

/** 상단 막대 오른쪽 "저장". 저장 중이면 진행 표시. */
@Composable
private fun SaveAction(state: ProfileEditUiState, onSave: () -> Unit) {
    if (state.isSaving) {
        RunkkSpinner(size = 20.dp, color = RunkkColors.Lime)
    } else {
        TextButton(onClick = onSave, enabled = state.canSave) {
            Text(
                stringResource(R.string.me_profile_edit_save),
                style = RunkkTextStyles.BodyStrong,
                color = if (state.canSave) RunkkColors.Lime else RunkkColors.TextSecondary
            )
        }
    }
}

/**
 * 맨 위 내 아바타 얼굴(보기만, R2-04·D86), 닉네임·한줄 소개 입력칸과 나이·성별 공개 스위치(G-2).
 * 사진 칸과 성향 태그는 편집 항목에서 뺐다(D-287, D-91).
 */
@Composable
private fun ProfileEditForm(
    state: ProfileEditUiState,
    onNicknameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onAgePublicChange: (Boolean) -> Unit,
    onGenderPublicChange: (Boolean) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onBirthYearChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileAvatar(state)
        RunkkTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            label = stringResource(R.string.me_profile_edit_nickname_label),
            placeholder = stringResource(R.string.me_profile_nickname_placeholder),
            supporting = stringResource(R.string.me_profile_edit_nickname_length)
        )
        RunkkTextField(
            value = state.intro,
            onValueChange = onIntroChange,
            label = stringResource(R.string.me_profile_intro_label),
            placeholder = stringResource(R.string.me_profile_intro_placeholder),
            maxLength = ProfileEditUiState.INTRO_MAX,
            supporting = stringResource(
                R.string.me_profile_intro_count,
                state.intro.length,
                ProfileEditUiState.INTRO_MAX
            ),
            imeAction = ImeAction.Done
        )
        // 나이 묶음(출생연도 + 공개)도 성별 묶음처럼 구분선으로 나눈다.
        HorizontalDivider(color = RunkkColors.Border)
        val resources = LocalContext.current.resources
        val thisYear = remember { Year.now().value }
        RunkkSelectField(
            label = stringResource(R.string.me_profile_birth_year_label),
            options = remember(thisYear) { (thisYear downTo OLDEST_BIRTH_YEAR).toList() },
            selected = state.birthYear,
            optionLabel = { SelectLabel(resources.getString(R.string.me_profile_birth_year_option, it)) },
            onSelect = { onBirthYearChange(it) }
        )
        val public = stringResource(R.string.me_profile_public)
        val private = stringResource(R.string.me_profile_private)
        RunkkSwitchRow(
            title = stringResource(R.string.me_profile_age_title),
            description = stringResource(
                R.string.me_profile_visibility_value,
                state.ageBand ?: RunkkFormat.EMPTY,
                if (state.isAgePublic) public else private
            ),
            isChecked = state.isAgePublic,
            onCheckedChange = onAgePublicChange
        )
        HorizontalDivider(color = RunkkColors.Border)
        // 성별 고르기(여성이면 기본 캐릭터가 여자)
        RunkkSegmentRow(
            options = GENDER_CHOICES.map { stringResource(genderLabelRes(it)) },
            selectedIndex = GENDER_CHOICES.indexOf(state.gender),
            onSelect = { onGenderChange(GENDER_CHOICES[it]) },
            groupLabel = stringResource(R.string.me_profile_gender_title)
        )
        RunkkSwitchRow(
            title = stringResource(R.string.me_profile_gender_title),
            description = stringResource(
                R.string.me_profile_visibility_value,
                state.gender?.let { stringResource(genderLabelRes(it)) } ?: RunkkFormat.EMPTY,
                if (state.isGenderPublic) public else private
            ),
            isChecked = state.isGenderPublic,
            onCheckedChange = onGenderPublicChange
        )
    }
}

/** 내 아바타 얼굴(지름 88dp). 누를 수 없는 장식이다. */
@Composable
private fun ProfileAvatar(state: ProfileEditUiState) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RunkkAvatarCircle(
            AVATAR_SIZE,
            modifier = Modifier.testTag(PROFILE_EDIT_AVATAR_TAG),
            background = RunkkColors.SurfacePressed
        ) {
            RunkkAvatar(
                state.faceKey,
                AvatarExpression.FACE,
                Modifier.size(AVATAR_SIZE),
                state.faceLayers,
                isFaceLoading = state.isFacePending
            )
        }
    }
}

/** 프로필 편집 맨 위 아바타의 시험 표지. */
internal const val PROFILE_EDIT_AVATAR_TAG = "profile-edit-avatar"

private val AVATAR_SIZE = 88.dp

@Preview(name = "F3 프로필 편집", widthDp = 390, heightDp = 844)
@Composable
private fun ProfileEditPreview() {
    RunkkTheme(reduceMotion = true) {
        ProfileEditScreen(
            state = ProfileEditUiState(
                isLoading = false,
                nickname = "달리는곰",
                intro = "퇴근 뒤 한강",
                ageBand = "20대",
                gender = Gender.FEMALE,
                isAgePublic = true
            ),
            onBack = {},
            onNicknameChange = {},
            onIntroChange = {},
            onSave = {},
            onErrorShown = {}
        )
    }
}

private val GENDER_CHOICES = listOf(Gender.MALE, Gender.FEMALE, Gender.UNDISCLOSED)

private const val OLDEST_BIRTH_YEAR = 1930

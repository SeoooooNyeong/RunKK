package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkChipGroup
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator

/** 프로필 편집(FR-007). */
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
        onTagToggled = viewModel::onTagToggled,
        onSave = viewModel::onSave,
        onErrorShown = viewModel::onErrorShown
    )
}

@Composable
fun ProfileEditScreen(
    state: ProfileEditUiState,
    onBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onTagToggled: (String) -> Unit,
    onSave: () -> Unit,
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
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.me_profile_edit_title), onBack = onBack)
            if (state.isLoading) {
                RunkkLoading(modifier = Modifier.weight(1f))
            } else {
                ProfileEditForm(
                    state = state,
                    onNicknameChange = onNicknameChange,
                    onIntroChange = onIntroChange,
                    onTagToggled = onTagToggled,
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkPrimaryButton(
                text = stringResource(R.string.me_profile_edit_save),
                onClick = onSave,
                isEnabled = state.canSave,
                isLoading = state.isSaving,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
}

/** 닉네임·소개 입력칸과 성향 태그 선택. */
@Composable
private fun ProfileEditForm(
    state: ProfileEditUiState,
    onNicknameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onTagToggled: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RunkkTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            label = stringResource(R.string.me_profile_edit_nickname_label),
            placeholder = stringResource(R.string.me_profile_nickname_placeholder),
            maxLength = SignUpUiState.NICKNAME_LENGTH.last
        )
        RunkkTextField(
            value = state.intro,
            onValueChange = onIntroChange,
            label = stringResource(R.string.me_profile_intro_label),
            placeholder = stringResource(R.string.me_profile_intro_placeholder),
            maxLength = SignUpUiState.INTRO_MAX,
            imeAction = ImeAction.Done
        )
        RunkkTags.runnerCategories.forEach { category ->
            RunkkChipGroup(title = stringResource(category.nameRes)) {
                category.tags.forEach { tag ->
                    RunkkChoiceChip(
                        stringResource(tag.labelRes),
                        isSelected = tag.id in state.tags,
                        onSelectedChange = { onTagToggled(tag.id) }
                    )
                }
            }
        }
    }
}

@Preview(name = "프로필 편집", widthDp = 390, heightDp = 844)
@Composable
private fun ProfileEditPreview() {
    RunkkTheme(reduceMotion = true) {
        ProfileEditScreen(
            state = ProfileEditUiState(
                isLoading = false,
                nickname = "달리는곰",
                intro = "퇴근 뒤 한강",
                tags = setOf("SOCIAL_RUN", "EVENING")
            ),
            onBack = {},
            onNicknameChange = {},
            onIntroChange = {},
            onTagToggled = {},
            onSave = {},
            onErrorShown = {}
        )
    }
}

package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator

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
        onProfilePublicChange = viewModel::onProfilePublicChange
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
    onProfilePublicChange: (Boolean) -> Unit = {}
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
            RunkkTopBar(title = stringResource(R.string.me_profile_edit_title), onBack = onBack) {
                SaveAction(state, onSave)
            }
            if (state.isLoading) {
                RunkkLoading(modifier = Modifier.weight(1f))
            } else {
                ProfileEditForm(
                    state = state,
                    onNicknameChange = onNicknameChange,
                    onIntroChange = onIntroChange,
                    onProfilePublicChange = onProfilePublicChange,
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
        CircularProgressIndicator(color = RunkkColors.Lime, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
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

/** 닉네임·한줄 소개 입력칸과 나이대·성별·소개 공개. 성향 태그는 편집 항목에서 뺐다(FR-007, D-91). */
@Composable
private fun ProfileEditForm(
    state: ProfileEditUiState,
    onNicknameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onProfilePublicChange: (Boolean) -> Unit,
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
        state.isProfilePublic?.let { isPublic ->
            RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
                RunkkSwitchRow(
                    title = stringResource(R.string.me_profile_public_title),
                    isChecked = isPublic,
                    onCheckedChange = onProfilePublicChange
                )
            }
        }
    }
}

@Preview(name = "F3 프로필 편집", widthDp = 390, heightDp = 844)
@Composable
private fun ProfileEditPreview() {
    RunkkTheme(reduceMotion = true) {
        ProfileEditScreen(
            state = ProfileEditUiState(
                isLoading = false,
                nickname = "달리는곰",
                intro = "퇴근 뒤 한강",
                isProfilePublic = true
            ),
            onBack = {},
            onNicknameChange = {},
            onIntroChange = {},
            onSave = {},
            onErrorShown = {}
        )
    }
}

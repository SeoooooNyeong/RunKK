package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes

@Composable
fun CrewNoticeWriteRoute(key: Routes.CrewNoticeWrite, viewModel: CrewNoticeWriteViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key.crewId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(state.isPosted) { if (state.isPosted) navigator.back() }
    CrewNoticeWriteScreen(
        state = state,
        actions = NoticeWriteActions(
            onBack = navigator::back,
            onBody = viewModel::onBody,
            onPinned = viewModel::onPinned,
            onSubmit = { viewModel.onSubmit(key.crewId) }
        )
    )
}

/** 공지 쓰기(와이어프레임 M1): 공지 내용과 글자 수, 상단에 고정 스위치, 지금 고정된 공지 수, 올리기. */
@Composable
fun CrewNoticeWriteScreen(state: CrewNoticeWriteUiState, actions: NoticeWriteActions, modifier: Modifier = Modifier) {
    WriteScaffold(
        state = WriteScaffoldState(
            title = stringResource(R.string.crew_notice_write_title),
            submitLabel = stringResource(R.string.crew_board_write_submit),
            isSubmitEnabled = state.canSubmit,
            isSaving = state.isSaving,
            error = state.error
        ),
        onBack = actions.onBack,
        onSubmit = actions.onSubmit,
        modifier = modifier
    ) {
        RunkkTextField(
            value = state.body,
            onValueChange = actions.onBody,
            label = stringResource(R.string.crew_notice_write_label),
            supporting = countLabel(state.body.codePointLength(), CrewNoticeWriteUiState.BODY_MAX),
            isSingleLine = false
        )
        RunkkSwitchRow(
            title = stringResource(R.string.crew_notice_write_pin),
            isChecked = state.isPinned,
            onCheckedChange = actions.onPinned,
            modifier = Modifier.clip(RunkkShapes.Small).background(MaterialTheme.colorScheme.surface)
        )
        state.pinnedCount?.let { count ->
            Text(
                stringResource(R.string.crew_notice_write_pinned_count, count, CrewNoticeWriteUiState.PIN_MAX),
                style = RunkkTextStyles.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

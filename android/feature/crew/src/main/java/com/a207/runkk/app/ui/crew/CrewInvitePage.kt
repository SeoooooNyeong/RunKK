package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar

/** 초대 코드로 가입(와이어 E5): 뒤로, 제목, 한 글자씩 여섯 칸, 가입하기. 시스템 뒤로 가기도 닫는다. */
@Composable
internal fun CrewInvitePage(state: CrewFindUiState, actions: CrewFindActions) {
    BackHandler(onBack = actions.onInviteClose)
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        RunkkTopBar(title = stringResource(R.string.crew_invite_title), onBack = actions.onInviteClose)
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                stringResource(R.string.crew_invite_heading),
                style = RunkkTextStyles.Headline,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            InviteCodeField(state.inviteCode, actions.onInviteCode, actions.onInviteSubmit)
        }
        RunkkPrimaryButton(
            stringResource(R.string.crew_invite_submit),
            onClick = actions.onInviteSubmit,
            isEnabled = state.inviteCode.length == INVITE_CODE_LENGTH,
            isLoading = state.isJoining,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

/** 입력칸 하나를 여섯 칸으로 그린다. 보조 기술에는 "초대 코드" 입력칸 하나로 읽힌다. */
@Composable
private fun InviteCodeField(code: String, onCode: (String) -> Unit, onDone: () -> Unit) {
    val label = stringResource(R.string.crew_invite_field)
    BasicTextField(
        value = code,
        onValueChange = onCode,
        singleLine = true,
        // 실제 입력 글자와 커서는 투명하게 두고 칸 그림으로 보인다.
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (code.length == INVITE_CODE_LENGTH) onDone() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        decorationBox = { inner ->
            Box {
                inner()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(INVITE_CODE_LENGTH) { index ->
                        CodeCell(code.getOrNull(index), isActive = index == code.length, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    )
}

/** 코드 한 칸: 글자, 지금 칸이면 포인트 테두리. */
@Composable
private fun CodeCell(char: Char?, isActive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(CELL_RATIO)
            .clip(RunkkShapes.Small)
            .background(RunkkColors.Surface)
            .border(
                if (isActive) 2.dp else 1.dp,
                if (isActive) RunkkColors.Lime else RunkkColors.InputBorder,
                RunkkShapes.Small
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(char?.toString().orEmpty(), style = RunkkTextStyles.display(28), color = RunkkColors.TextPrimary)
    }
}

internal const val INVITE_CODE_LENGTH = 6
private const val CELL_RATIO = 0.82f

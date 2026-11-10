package com.a207.runkk.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 입력 줄(와이어프레임 M4): "메시지 보내기" 자리 문구가 든 입력칸, 둥근 보내기 단추, 아래 오른쪽 글자 수(코드 포인트).
 * 500자를 넘기면 보내기를 막고 그 이유를 글자 수 자리에 보인다(FR-208).
 */
@Composable
internal fun ChatComposer(state: ChatUiState, actions: ChatActions) {
    val overLimit = if (state.isOverLimit) stringResource(R.string.chat_over_limit, ChatUiState.CONTENT_MAX) else null
    val inputLabel = stringResource(R.string.chat_input_label)
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = state.draft,
                onValueChange = actions.onDraft,
                textStyle = RunkkTextStyles.text(13, lineHeight = 16).copy(color = RunkkColors.TextPrimary),
                cursorBrush = SolidColor(RunkkColors.TextPrimary),
                maxLines = MAX_LINES,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = inputLabel
                        overLimit?.let { error(it) }
                    },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RunkkShapes.Small)
                            .border(
                                1.dp,
                                if (overLimit !=
                                    null
                                ) {
                                    RunkkColors.Violet
                                } else {
                                    RunkkColors.Border
                                },
                                RunkkShapes.Small
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (state.draft.isEmpty()) {
                            Text(
                                stringResource(R.string.chat_input_placeholder),
                                style = RunkkTextStyles.text(13, lineHeight = 16),
                                color = RunkkColors.TextSecondary
                            )
                        }
                        inner()
                    }
                }
            )
            SendButton(isEnabled = state.canSend, onSend = actions.onSend)
        }
        Text(
            overLimit ?: stringResource(R.string.chat_count, state.draftLength, ChatUiState.CONTENT_MAX),
            style = RunkkTextStyles.Caption,
            color = if (overLimit != null) RunkkColors.Violet else RunkkColors.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 52.dp)
                .then(if (overLimit != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
        )
    }
}

/** 둥근 보내기 단추(보이는 크기 44dp, 터치 영역 48dp). 보낼 수 없으면 흐리게 하고 누름을 막는다. */
@Composable
private fun SendButton(isEnabled: Boolean, onSend: () -> Unit) {
    val label = stringResource(R.string.chat_send)
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onSend)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer { alpha = if (isEnabled) 1f else DISABLED_ALPHA }
                .clip(CircleShape)
                .background(RunkkColors.TextPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                ChatIcons.ArrowUp,
                contentDescription = null,
                tint = RunkkColors.Surface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 입력칸이 늘어나는 최대 줄 수. */
private const val MAX_LINES = 4

private const val DISABLED_ALPHA = 0.4f

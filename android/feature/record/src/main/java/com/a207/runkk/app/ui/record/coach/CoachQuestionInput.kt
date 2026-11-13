package com.a207.runkk.app.ui.record.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkAlpha
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.ui.record.R

/**
 * 질문 입력 줄(피그마 G-25 Input): 테두리 위에 "질문" 라벨이 걸친 입력 칸(모서리 4, 높이 56) + 12 + 원형 보내기(48 누름 칸, 40 원).
 * 아래 줄에 오류(왼쪽)와 글자 수 "n / 500"(오른쪽, 12 보조색)을 둔다. 보낼 수 없으면 보내기는 흐린 회색 원이다.
 */
@Composable
internal fun CoachQuestionInput(state: CoachChatUiState, onSend: (String) -> Unit) {
    var question by rememberSaveable(state.conversationId) { mutableStateOf("") }
    val length = question.codePointCount(0, question.length)
    val isTooLong = length > CoachChatUiState.MAX_QUESTION_LENGTH
    val error = if (isTooLong) stringResource(R.string.record_chat_question_invalid) else state.inputError?.asString()
    val placeholder = stringResource(
        if (state.conversationId != null && state.turns.isEmpty()) {
            R.string.record_chat_placeholder_continue
        } else {
            R.string.record_chat_placeholder
        }
    )
    Column(Modifier.fillMaxWidth().padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 8.dp, bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestionField(
                value = question,
                onValueChange = { question = it },
                placeholder = placeholder,
                error = error,
                modifier = Modifier.weight(1f)
            )
            SendButton(
                isEnabled = state.canAsk && CoachChatUiState.accepts(question),
                onClick = {
                    onSend(question)
                    question = ""
                },
                // 56 칸 가운데에 맞춘다(48 누름 칸 + 아래 4).
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            if (error != null) {
                Text(
                    error,
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender,
                    modifier = Modifier.weight(1f).padding(end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.record_chat_length, length, CoachChatUiState.MAX_QUESTION_LENGTH),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/**
 * 테두리 위에 라벨이 걸친 입력 칸(피그마 M3 Outlined Text field). 라벨은 입력칸 장식 안에 두어 입력칸 노드가 "질문"을 이름으로 읽는다.
 * 여러 줄을 받되 5줄까지만 늘어난다.
 */
@Composable
private fun QuestionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String?,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.record_chat_question)
    val borderColor = if (error != null) RunkkColors.Lavender else RunkkColors.Outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { if (error != null) error(error) },
        textStyle = FIELD_TEXT.copy(color = RunkkColors.TextPrimary),
        cursorBrush = SolidColor(RunkkColors.Lime),
        maxLines = 5,
        decorationBox = { inner ->
            Box {
                Box(
                    modifier = Modifier
                        .padding(top = LABEL_HALF)
                        .fillMaxWidth()
                        .heightIn(min = FIELD_HEIGHT)
                        .border(1.dp, borderColor, FIELD_SHAPE)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) Text(placeholder, style = FIELD_TEXT, color = RunkkColors.TextSecondary)
                    inner()
                }
                // 라벨 바탕을 화면 배경으로 칠해 테두리 선을 끊는다.
                Text(
                    label,
                    style = RunkkTextStyles.text(12, lineHeight = 16),
                    color = if (error != null) RunkkColors.Lavender else RunkkColors.TextSecondary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .background(RunkkColors.Background)
                        .padding(horizontal = 4.dp)
                )
            }
        }
    )
}

/** 원형 보내기(누름 칸 48, 보이는 원 40, 아이콘 24). 켜지면 라임 원, 꺼지면 글자색 10% 원에 38% 아이콘. */
@Composable
private fun SendButton(isEnabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.record_chat_send)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isEnabled) RunkkColors.LimeFill else RunkkColors.TextPrimary.copy(alpha = SEND_OFF_FILL_ALPHA)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.Send,
                contentDescription = null,
                tint = if (isEnabled) {
                    RunkkColors.OnLimeFill
                } else {
                    RunkkColors.TextPrimary.copy(
                        alpha = RunkkAlpha.DisabledContent
                    )
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private val FIELD_TEXT = RunkkTextStyles.text(16, lineHeight = 24)
private val FIELD_SHAPE = RoundedCornerShape(4.dp)
private val FIELD_HEIGHT = 56.dp

/** 라벨 줄 높이(16)의 절반: 테두리가 라벨 가운데를 지나게 칸을 이만큼 내린다. */
private val LABEL_HALF = 8.dp

/** 꺼진 보내기 원 채움(글자색 10%, Material 꺼짐 채움). */
private const val SEND_OFF_FILL_ALPHA = 0.1f

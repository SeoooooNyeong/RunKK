package com.a207.runkk.app.ui.record.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.ui.record.R

/** D-451~D-453의 최소 화면. 실제 저장소와 경로 연결은 서버 계약 연동 뒤에 한다. */
@Composable
fun CoachChatScreen(state: CoachChatUiState, actions: CoachChatActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(RunkkColors.Background).imePadding()) {
        RunkkTopBar(title = stringResource(R.string.record_chat_title), onBack = actions.onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RunkkSecondaryButton(
                    stringResource(R.string.record_chat_new),
                    actions.onNewConversation,
                    isEnabled = !state.isSending
                )
            }
            item { ConversationList(state, actions) }
            if (state.turns.isEmpty()) {
                item {
                    Text(
                        stringResource(
                            if (state.conversationId ==
                                null
                            ) {
                                R.string.record_chat_empty
                            } else {
                                R.string.record_chat_continue
                            }
                        ),
                        style = RunkkTextStyles.Body,
                        color = RunkkColors.TextSecondary
                    )
                }
            }
            items(state.turns) { turn -> ChatTurn(turn) }
            item {
                if (state.isSending) {
                    Text(
                        stringResource(R.string.record_chat_answering),
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                state.failure?.let { ChatFailure(it, actions) }
            }
        }
        QuestionInput(state, actions.onSend)
    }
}

@Composable
private fun ConversationList(state: CoachChatUiState, actions: CoachChatActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isLoadingConversations) {
            Text(
                stringResource(R.string.record_chat_loading),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        state.conversationError?.let {
            Text(it.asString(), style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
            RunkkSecondaryButton(stringResource(R.string.record_chat_reload), actions.onRefreshConversations)
        }
        state.conversations.forEach { conversation ->
            RunkkSecondaryButton(
                conversation.firstQuestion,
                { actions.onOpenConversation(conversation.conversationId) },
                isEnabled = !state.isSending
            )
        }
    }
}

@Composable
private fun ChatTurn(turn: CoachChatTurn) {
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.record_chat_my_question),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.semantics { heading() }
        )
        Text(turn.question, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        if (turn.answer.isNotEmpty()) {
            Text(
                stringResource(R.string.record_chat_title),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lime,
                modifier = Modifier.semantics { heading() }
            )
            Text(turn.answer, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        }
    }
}

@Composable
private fun ChatFailure(failure: CoachFailure, actions: CoachChatActions) {
    val message = when (failure) {
        CoachFailure.RETRY -> R.string.record_chat_retry_message
        CoachFailure.QUOTA -> R.string.record_chat_quota
        CoachFailure.EXPIRED -> R.string.record_chat_expired
        CoachFailure.TOO_LONG -> R.string.record_chat_question_invalid
        CoachFailure.CONSENT_REQUIRED -> R.string.record_chat_consent
        CoachFailure.FEATURE_CLOSED -> R.string.record_chat_closed
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(message),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        when (failure) {
            CoachFailure.RETRY -> RunkkSecondaryButton(stringResource(R.string.record_chat_retry), actions.onRetry)

            CoachFailure.CONSENT_REQUIRED -> RunkkSecondaryButton(
                stringResource(R.string.record_chat_consents),
                actions.onConsents
            )

            else -> Unit
        }
    }
}

@Composable
private fun QuestionInput(state: CoachChatUiState, onSend: (String) -> Unit) {
    var question by rememberSaveable(state.conversationId) { mutableStateOf("") }
    val isTooLong = question.codePointCount(0, question.length) > CoachChatUiState.MAX_QUESTION_LENGTH
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkTextField(
            value = question,
            onValueChange = { question = it },
            label = stringResource(R.string.record_chat_question),
            isSingleLine = false,
            error = if (isTooLong) {
                stringResource(
                    R.string.record_chat_question_invalid
                )
            } else {
                state.inputError?.asString()
            }
        )
        RunkkPrimaryButton(
            text = stringResource(R.string.record_chat_send),
            onClick = {
                onSend(question)
                question = ""
            },
            modifier = Modifier.fillMaxWidth(),
            isEnabled = state.canAsk && CoachChatUiState.accepts(question),
            isLoading = state.isSending
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CoachChatPreview() {
    RunkkTheme {
        CoachChatScreen(
            CoachChatUiState(turns = listOf(CoachChatTurn("오늘은 어떻게 달릴까요?", "천천히 달리며 회복해 보세요.", true))),
            CoachChatActions()
        )
    }
}

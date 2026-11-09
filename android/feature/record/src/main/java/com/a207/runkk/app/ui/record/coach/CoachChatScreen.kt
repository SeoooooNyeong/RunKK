package com.a207.runkk.app.ui.record.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** 코치의 진입 안내와 대화 화면(D-451~D-453). */
@Composable
fun CoachChatScreen(state: CoachChatUiState, actions: CoachChatActions, modifier: Modifier = Modifier) {
    if (state.failure == CoachFailure.CONSENT_REQUIRED) {
        CoachConsentRequired(actions, modifier)
        return
    }
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

/** G-24. 같은 코치 경로에서 질문 입력을 대신하는 안내다. */
@Composable
private fun CoachConsentRequired(actions: CoachChatActions, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(
            title = stringResource(R.string.record_chat_title),
            onBack = actions.onBack,
            isTitleCentered = true
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp)) {
            RunkkCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.record_chat_consent_required_title),
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.record_chat_consent_required_body),
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextSecondary
                )
                Text(
                    stringResource(R.string.record_chat_consent_retention),
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), Arrangement.spacedBy(8.dp)) {
            RunkkSecondaryButton(
                stringResource(R.string.record_chat_consent_cancel),
                actions.onBack,
                Modifier.weight(1f)
            )
            RunkkPrimaryButton(
                stringResource(R.string.record_chat_consent_settings),
                actions.onConsents,
                Modifier.weight(1f)
            )
        }
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
        CoachFailure.CONSENT_REQUIRED -> R.string.record_chat_consent_required_title
        CoachFailure.FEATURE_CLOSED -> R.string.record_chat_closed
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(message),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (failure == CoachFailure.RETRY) {
            RunkkSecondaryButton(stringResource(R.string.record_chat_retry), actions.onRetry)
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

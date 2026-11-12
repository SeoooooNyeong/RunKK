package com.a207.runkk.app.ui.record.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.domain.coach.CoachFailure
import com.a207.runkk.app.ui.record.R

/**
 * 코치의 진입 안내와 대화 화면(D-451~D-453). 피그마 G-37(새 대화)·G-36(이전 대화 이어가기)·G-25(대화)·G-26(답변 실패):
 * 가운데 제목 바, 위 "새 대화 · 이전 대화" 알약 두 개, 내 질문은 오른쪽 라임 말풍선·코치 답은 왼쪽 흰 말풍선, 아래 질문 칸과 보내기.
 * "이전 대화"를 누르면 같은 경로 안에서 목록(G-28·G-40~G-42)으로 바꿔 그린다.
 */
@Composable
fun CoachChatScreen(state: CoachChatUiState, actions: CoachChatActions, modifier: Modifier = Modifier) {
    if (state.failure == CoachFailure.CONSENT_REQUIRED) {
        CoachConsentRequired(actions, modifier)
        return
    }
    var isHistoryOpen by rememberSaveable { mutableStateOf(false) }
    if (isHistoryOpen) {
        BackHandler { isHistoryOpen = false }
        CoachChatHistory(
            state = state,
            onBack = { isHistoryOpen = false },
            onOpen = { id ->
                actions.onOpenConversation(id)
                isHistoryOpen = false
            },
            onNewConversation = {
                actions.onNewConversation()
                isHistoryOpen = false
            },
            onRetry = actions.onRefreshConversations,
            modifier = modifier
        )
        return
    }
    val listState = rememberLazyListState()
    // 지난 문답을 다 읽었거나 새 질문·이어지는 답이 오면 마지막 말풍선이 보이게 내린다.
    LaunchedEffect(state.history.size, state.turns.size, state.turns.lastOrNull()?.answer?.length, state.failure) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if ((state.turns.isNotEmpty() || state.history.isNotEmpty()) && last >= 0) listState.scrollToItem(last)
    }
    Column(modifier.fillMaxSize().background(RunkkColors.Background).imePadding()) {
        RunkkTopBar(title = stringResource(R.string.record_chat_title), onBack = actions.onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ConversationActions(
                    isEnabled = !state.isSending,
                    onNewConversation = actions.onNewConversation,
                    onHistory = { isHistoryOpen = true }
                )
            }
            if (state.turns.isEmpty()) item { StartCard(state, actions.onReloadHistory) }
            // 이전 대화를 열었으면 지난 문답을 먼저 그리고 그 아래로 이어서 묻는다(S15P31A207-300).
            items(state.history) { turn -> ChatTurn(turn, isStreaming = false) }
            itemsIndexed(state.turns) { index, turn ->
                ChatTurn(turn, isStreaming = state.isSending && index == state.turns.lastIndex && !turn.isComplete)
            }
            state.failure?.let { failure -> item { ChatFailure(failure, actions) } }
        }
        CoachQuestionInput(state, actions.onSend)
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

/** "새 대화 · 이전 대화"(피그마 Outline Medium 48 두 개, 사이 8). 답을 만드는 동안은 회색으로 꺼진다(G-25). */
@Composable
private fun ConversationActions(isEnabled: Boolean, onNewConversation: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkButton(
            text = stringResource(R.string.record_chat_new),
            onClick = onNewConversation,
            modifier = Modifier.weight(1f),
            style = RunkkButtonStyle.Outline,
            size = RunkkButtonSize.Medium,
            isEnabled = isEnabled
        )
        RunkkButton(
            text = stringResource(R.string.record_chat_history),
            onClick = onHistory,
            modifier = Modifier.weight(1f),
            style = RunkkButtonStyle.Outline,
            size = RunkkButtonSize.Medium,
            isEnabled = isEnabled
        )
    }
}

/**
 * 질문 전 안내 카드(모서리 24, 안쪽 20, 사이 12). 새 대화면 흰 카드(G-37), 고른 이전 대화를 이어가면 연보라 카드에
 * "첫 질문 · 마지막 질문일"을 함께 보인다(G-36). 이어가는 카드는 지난 문답을 읽는 중·실패(다시 불러오기)도 함께 알린다.
 */
@Composable
private fun StartCard(state: CoachChatUiState, onReloadHistory: () -> Unit) {
    val isContinuing = state.conversationId != null
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = PaddingValues(20.dp),
        background = if (isContinuing) RunkkColors.AccentContainer else RunkkColors.Surface,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(if (isContinuing) R.string.record_chat_continue else R.string.record_chat_new_title),
            style = CARD_TITLE,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        if (isContinuing) {
            val formatter = rememberChatDateFormatter()
            state.conversations.firstOrNull { it.conversationId == state.conversationId }?.let { picked ->
                Text(
                    stringResource(
                        R.string.record_chat_continue_meta,
                        picked.firstQuestion,
                        picked.lastAskedAt.format(formatter)
                    ),
                    style = CARD_BODY,
                    color = RunkkColors.TextSecondary
                )
            }
            HistoryStatus(state, onReloadHistory)
        } else {
            Text(stringResource(R.string.record_chat_empty), style = CARD_BODY, color = RunkkColors.TextSecondary)
            Text(stringResource(R.string.record_chat_new_body), style = CARD_BODY, color = RunkkColors.TextSecondary)
        }
    }
}

/** 이어가는 카드 아래 줄: 지난 문답을 읽는 중, 읽기 실패와 다시 불러오기, 다 읽은 뒤 이어서 묻는 안내. */
@Composable
private fun HistoryStatus(state: CoachChatUiState, onReloadHistory: () -> Unit) {
    val error = state.historyError
    when {
        state.isLoadingHistory -> Text(
            stringResource(R.string.record_chat_messages_loading),
            style = CARD_BODY,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        error != null -> {
            Text(
                stringResource(R.string.record_chat_messages_error),
                style = CARD_BODY,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text(error.asString(), style = CARD_BODY, color = RunkkColors.TextSecondary)
            RunkkButton(
                text = stringResource(R.string.record_chat_reload),
                onClick = onReloadHistory,
                modifier = Modifier.fillMaxWidth(),
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium
            )
        }

        else -> Text(
            stringResource(
                if (state.history.isEmpty()) {
                    R.string.record_chat_continue_body_empty
                } else {
                    R.string.record_chat_continue_body
                }
            ),
            style = CARD_BODY,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 문답 한 쌍: 내 질문(오른쪽 라임) 다음 코치 답(왼쪽 흰). 답을 받는 중이면 끝에 커서를 붙인다(G-25). */
@Composable
private fun ChatTurn(turn: CoachChatTurn, isStreaming: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 조회 상한으로 앞 질문이 잘린 지난 답은 코치 말풍선만 그린다.
        if (turn.question.isNotEmpty()) MyBubble(turn.question)
        when {
            turn.answer.isNotEmpty() -> CoachBubble {
                Text(
                    if (isStreaming) turn.answer + STREAM_CURSOR else turn.answer,
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextPrimary,
                    // 커서 글자는 보조 기술에 읽히지 않게 답 글만 남긴다.
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(turn.answer) }
                )
            }

            isStreaming -> CoachBubble {
                Text(
                    stringResource(R.string.record_chat_answering) + STREAM_CURSOR,
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

/** 내 질문 말풍선: 라임 채움, 모서리 24, 안쪽 14·10, 오른쪽 정렬. */
@Composable
private fun MyBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = RunkkTextStyles.Body,
            color = RunkkColors.OnLimeFill,
            modifier = Modifier
                .widthIn(max = MY_BUBBLE_MAX_WIDTH)
                .clip(BUBBLE_SHAPE)
                .background(RunkkColors.LimeFill)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** 코치 말풍선: 흰 채움 + 테두리 1, 모서리 24, 안쪽 14·10, 왼쪽 정렬. */
@Composable
private fun CoachBubble(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .widthIn(max = COACH_BUBBLE_MAX_WIDTH)
                .clip(BUBBLE_SHAPE)
                .background(RunkkColors.Surface)
                .border(1.dp, RunkkColors.Outline, BUBBLE_SHAPE)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** 실패 안내는 코치 말풍선 안에 문구와 복구 버튼으로 보인다(G-26·G-43). */
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
    CoachBubble {
        Text(
            stringResource(message),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (failure == CoachFailure.RETRY) {
            RunkkButton(
                text = stringResource(R.string.record_chat_retry),
                onClick = actions.onRetry,
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium
            )
        }
    }
}

/** 본문 좌우 여백(피그마 20). */
internal val PAGE_GUTTER = 20.dp

/** 안내 카드 제목(16 Bold, 줄 24)과 본문(14, 줄 21). */
internal val CARD_TITLE = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 24)
internal val CARD_BODY = RunkkTextStyles.text(14, lineHeight = 21)

private val BUBBLE_SHAPE = RoundedCornerShape(24.dp)

/** 말풍선 최대 폭(피그마 글 폭 200·250 + 안쪽 여백). */
private val MY_BUBBLE_MAX_WIDTH = 228.dp
private val COACH_BUBBLE_MAX_WIDTH = 280.dp

/** 답을 받는 중임을 보이는 커서(G-25 "▍"). */
private const val STREAM_CURSOR = " ▍"

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CoachChatPreview() {
    RunkkTheme {
        CoachChatScreen(
            CoachChatUiState(
                conversationId = "c1",
                turns = listOf(
                    CoachChatTurn("어제 러닝 어땠어?", "후반 3km에서 페이스를 잘 지켰어요. 같은 심박에서 지난주보다 12초 빨라졌어요.", true),
                    CoachChatTurn("이번 주에 뭘 하면 좋을까?", "회복 러닝을 한 번 넣고")
                ),
                isSending = true
            ),
            CoachChatActions()
        )
    }
}

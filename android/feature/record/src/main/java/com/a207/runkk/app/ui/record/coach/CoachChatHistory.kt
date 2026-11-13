package com.a207.runkk.app.ui.record.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.domain.coach.CoachConversation
import com.a207.runkk.app.ui.record.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 이전 대화 목록(피그마 G-28, 읽는 중 G-40, 없음 G-41, 실패 G-42). 위에 가운데 정렬 안내(12 보조색), 아래 흰 카드 목록(모서리 20)이다.
 * 대화를 고르면 [onOpen]으로 그 대화를 열고 코치 화면으로 돌아간다.
 */
@Composable
internal fun CoachChatHistory(
    state: CoachChatUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val error = state.conversationError
    val isLoading = state.isLoadingConversations && state.conversations.isEmpty()
    val hasList = error == null && !isLoading && state.conversations.isNotEmpty()
    Column(modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.record_chat_history), onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 8.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    stringResource(
                        if (hasList) R.string.record_chat_history_guide else R.string.record_chat_history_retention
                    ),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when {
                error != null -> item {
                    HistoryNotice(
                        title = stringResource(R.string.record_chat_history_error),
                        body = error.asString(),
                        actionLabel = stringResource(R.string.record_chat_reload),
                        onAction = onRetry
                    )
                }

                isLoading -> item {
                    HistoryNotice(
                        title = stringResource(R.string.record_chat_loading),
                        body = stringResource(R.string.record_chat_history_wait)
                    )
                }

                state.conversations.isEmpty() -> item {
                    HistoryNotice(
                        title = stringResource(R.string.record_chat_history_empty),
                        body = stringResource(R.string.record_chat_history_empty_body),
                        actionLabel = stringResource(R.string.record_chat_new),
                        onAction = onNewConversation
                    )
                }

                else -> items(state.conversations, key = { it.conversationId }) { conversation ->
                    ConversationItem(conversation) { onOpen(conversation.conversationId) }
                }
            }
        }
    }
}

/** 이전 대화 한 줄(흰 카드, 모서리 20): 첫 질문(16 Medium), 마지막 질문일, "이 대화에서 이어가기 ›"(14 보조색). */
@Composable
private fun ConversationItem(conversation: CoachConversation, onClick: () -> Unit) {
    val formatter = rememberChatDateFormatter()
    val continueLabel = stringResource(R.string.record_chat_history_continue)
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RunkkShapes.Large,
        contentPadding = PaddingValues(16.dp),
        onClick = onClick,
        onClickLabel = continueLabel,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            conversation.firstQuestion,
            style = RunkkTextStyles.text(16, FontWeight.Medium, lineHeight = 24),
            color = RunkkColors.TextPrimary
        )
        Column {
            Text(conversation.lastAskedAt.format(formatter), style = ITEM_META, color = RunkkColors.TextSecondary)
            Text(continueLabel, style = ITEM_META, color = RunkkColors.TextSecondary)
        }
    }
}

/** 목록 대신 보이는 안내 카드(모서리 24, 안쪽 20): 제목·설명과 복구 버튼(있을 때, Outline Medium 꽉 찬 폭). */
@Composable
private fun HistoryNotice(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = CARD_TITLE,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Text(body, style = CARD_BODY, color = RunkkColors.TextSecondary)
        if (actionLabel != null && onAction != null) {
            RunkkButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium
            )
        }
    }
}

private val ITEM_META = RunkkTextStyles.text(14, lineHeight = 20)

/** 대화 날짜 서식("M월 d일", 문자열 리소스). 코치 묶음은 ui.record를 가져오지 않으므로(ArchitectureTest) 여기에 둔다. */
@Composable
internal fun rememberChatDateFormatter(): DateTimeFormatter {
    val pattern = stringResource(R.string.record_chat_history_date)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
}

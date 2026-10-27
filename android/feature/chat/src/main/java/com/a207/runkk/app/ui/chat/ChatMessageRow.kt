package com.a207.runkk.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ReactionKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 메시지 한 건(와이어프레임 M4): 남의 메시지는 왼쪽에 아바타 자리·이름·말풍선·시각, 내 메시지는 오른쪽에 시각·말풍선. 리액션은 수가
 * 있는 것만 말풍선 아래 칩으로 보이고 눌러 달고 뗀다. 말풍선을 길게 누르면 메뉴(좋아요 달기, 하트 달기, 삭제, 신고)가 열린다.
 */
@Composable
internal fun ChatMessageRow(message: ChatMessage, actions: ChatActions) {
    if (message.isMine) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MessageBody(message, actions)
            MessageFooter(message, actions)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthorAvatar(message.authorName)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    message.authorName,
                    style = RunkkTextStyles.text(12, FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                MessageBody(message, actions)
                MessageFooter(message, actions)
            }
        }
    }
}

/** 남의 메시지 앞 아바타 자리(이름 첫 글자). 보조 기술에는 말풍선이 작성자를 읽으므로 숨긴다. */
@Composable
private fun AuthorAvatar(name: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1), style = RunkkTextStyles.text(13, FontWeight.Bold), color = colors.onSurfaceVariant)
    }
}

/** 말풍선과 시각, 길게 눌러 여는 메시지 메뉴. 보조 기술에는 "작성자, 시각, 내용" 한 문장과 길게 누르기 동작으로 읽힌다. */
@Composable
private fun MessageBody(message: ChatMessage, actions: ChatActions) {
    val colors = MaterialTheme.colorScheme
    val author = if (message.isMine) stringResource(R.string.chat_me) else message.authorName
    val time = timeLabel(message.sentAt)
    val content = if (message.isRemoved) stringResource(R.string.chat_removed) else message.content
    val description = stringResource(R.string.chat_message_description, author, time, content)
    val menuLabel = stringResource(R.string.chat_menu)
    val hasMenu = !message.isRemoved && message.sendState == ChatMessage.SendState.SENT
    var isMenuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box {
        Row(
            Modifier
                .clearAndSetSemantics {
                    contentDescription = description
                    if (hasMenu) {
                        onLongClick(menuLabel) {
                            isMenuOpen = true
                            true
                        }
                    }
                }
                .then(
                    if (hasMenu) {
                        Modifier.pointerInput(message.listKey()) {
                            detectTapGestures(onLongPress = { isMenuOpen = true })
                        }
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (message.isMine) Text(time, style = RunkkTextStyles.Micro, color = colors.onSurfaceVariant)
            Box(
                Modifier
                    .widthIn(max = 240.dp)
                    .clip(shape)
                    .background(if (message.isMine && !message.isRemoved) colors.primaryContainer else colors.surface)
                    .border(1.dp, if (message.isMine) colors.outline else colors.outlineVariant, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    content,
                    style = if (message.isRemoved) {
                        RunkkTextStyles.Body.copy(fontStyle = FontStyle.Italic)
                    } else {
                        RunkkTextStyles.Body
                    },
                    color = if (message.isRemoved) colors.onSurfaceVariant else colors.onSurface
                )
            }
            if (!message.isMine) Text(time, style = RunkkTextStyles.Micro, color = colors.onSurfaceVariant)
        }
        MessageMenu(message, isMenuOpen, onClose = { isMenuOpen = false }, actions = actions)
    }
}

/** 길게 누르면 열리는 메뉴: 위 줄에 리액션 두 가지(👍·❤️), 아래에 삭제(canDelete)·신고(남의 메시지). */
@Composable
private fun MessageMenu(message: ChatMessage, isOpen: Boolean, onClose: () -> Unit, actions: ChatActions) {
    val colors = MaterialTheme.colorScheme
    val author = if (message.isMine) stringResource(R.string.chat_me) else message.authorName
    DropdownMenu(expanded = isOpen, onDismissRequest = onClose, modifier = Modifier.background(colors.surface)) {
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReactionKind.entries.forEach { kind ->
                val isOn = kind in message.myReactions
                val label = stringResource(
                    if (isOn) R.string.chat_reaction_remove else R.string.chat_reaction_add,
                    stringResource(kind.labelRes())
                )
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isOn) colors.primaryContainer else colors.surface)
                        .toggleable(value = isOn, role = Role.Checkbox, onValueChange = {
                            onClose()
                            actions.onReaction(message, kind)
                        })
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Text(kind.emoji(), style = RunkkTextStyles.text(18))
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        if (message.canDelete) {
            val description = stringResource(R.string.chat_delete_description, author)
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.chat_delete), style = RunkkTextStyles.Body, color = colors.onSurface)
                },
                onClick = {
                    onClose()
                    actions.onDeleteAsk(message)
                },
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
        if (!message.isMine) {
            val description = stringResource(R.string.chat_report_description, author)
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.chat_report), style = RunkkTextStyles.Body, color = colors.onSurface)
                },
                onClick = {
                    onClose()
                    actions.onReportAsk(message)
                },
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
    }
}

/** 말풍선 아래: 보내는 중, 보내기 실패와 다시 보내기, 또는 수가 있는 리액션 칩. */
@Composable
private fun MessageFooter(message: ChatMessage, actions: ChatActions) {
    when {
        message.isRemoved -> Unit

        message.sendState == ChatMessage.SendState.SENDING -> Text(
            stringResource(R.string.chat_sending),
            style = RunkkTextStyles.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        message.sendState == ChatMessage.SendState.FAILED -> FailedRow(message, actions)

        else -> {
            val shown = ReactionKind.entries.filter { (message.reactions[it] ?: 0) > 0 }
            if (shown.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { kind -> ReactionChip(message, kind, actions) }
                }
            }
        }
    }
}

/** 보내기 실패 표시와 다시 보내기(D-160 (3)). 실패 문구는 바로 알린다. */
@Composable
private fun FailedRow(message: ChatMessage, actions: ChatActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.chat_failed),
            style = RunkkTextStyles.text(12, FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        RunkkTextLink(
            stringResource(R.string.chat_retry),
            onClick = { actions.onRetry(message) },
            contentDescription = stringResource(R.string.chat_retry_description, message.content.take(SNIPPET_CHARS))
        )
    }
}

/** 리액션 칩 하나: 누르면 달고 다시 누르면 뗀다. 보조 기술에는 "좋아요 3개, 누름"으로 읽힌다(FR-210). */
@Composable
private fun ReactionChip(message: ChatMessage, kind: ReactionKind, actions: ChatActions) {
    val colors = MaterialTheme.colorScheme
    val isOn = kind in message.myReactions
    val count = message.reactions[kind] ?: 0
    val description = stringResource(R.string.chat_reaction_description, stringResource(kind.labelRes()), count)
    val stateText = stringResource(if (isOn) R.string.chat_reaction_on else R.string.chat_reaction_off)
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .toggleable(value = isOn, role = Role.Checkbox, onValueChange = { actions.onReaction(message, kind) })
            .semantics {
                contentDescription = description
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${kind.emoji()} $count",
            style = RunkkTextStyles.text(12, if (isOn) FontWeight.Bold else FontWeight.Normal),
            color = if (isOn) colors.primary else colors.onSurface,
            modifier = Modifier
                .clip(shape)
                .background(if (isOn) colors.primaryContainer else colors.surface)
                .border(if (isOn) 2.dp else 1.dp, if (isOn) colors.primary else colors.outline, shape)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun ReactionKind.labelRes(): Int =
    if (this == ReactionKind.LIKE) R.string.chat_reaction_like else R.string.chat_reaction_heart

private fun ReactionKind.emoji(): String = if (this == ReactionKind.LIKE) LIKE_EMOJI else HEART_EMOJI

/** "오후 6:10". 틀은 문자열 리소스(chat_time_pattern)다. */
@Composable
private fun timeLabel(at: Instant): String = at.atZone(ZoneId.systemDefault()).format(
    DateTimeFormatter.ofPattern(stringResource(R.string.chat_time_pattern), Locale.KOREAN)
)

/** 다시 보내기 단추 이름에 넣을 메시지 앞부분 글자 수. */
private const val SNIPPET_CHARS = 20

private const val LIKE_EMOJI = "👍"
private const val HEART_EMOJI = "❤️"

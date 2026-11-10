package com.a207.runkk.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.chat.ReactionKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 메시지 한 건(피그마 F-09·F-09c·F-10): 남의 메시지는 왼쪽에 아바타 32·이름(크루장이면 「크루장」 배지)·말풍선(surfaceHigh)·시각, 내 메시지는
 * 오른쪽에 시각·흰 말풍선. 반응은 수가 있는 것만 말풍선 아래 알약으로 보이고 누르면 달고 떼며, 길게 누르면 반응한 사람 시트를 연다
 * ([임시:D-성-24]). 말풍선을 길게 누르면 메뉴(반응 세 가지, 삭제, 신고)가 말풍선 바로 위에 열린다. 러닝 완료 시스템 메시지는 가운데 두 줄이다.
 */
@Composable
internal fun ChatMessageRow(message: ChatMessage, actions: ChatActions) {
    when {
        message.isSystem -> SystemMessage(message)

        message.isMine -> Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MessageBody(message, actions)
            MessageFooter(message, actions)
        }

        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthorAvatar()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AuthorName(message)
                MessageBody(message, actions)
                MessageFooter(message, actions)
            }
        }
    }
}

/** 남의 메시지 앞 아바타 자리(피그마 32 원 surfaceHigh). 장식이라 보조 기술에는 숨긴다(말풍선이 작성자를 읽는다). */
@Composable
private fun AuthorAvatar() {
    Box(Modifier.size(32.dp).clip(CircleShape).background(RunkkColors.SurfaceHigh).clearAndSetSemantics {})
}

/** 이름 12 Bold와 크루장 배지(테두리 1, r8, 좌우 6·위아래 1, 10 Regular). 보조 기술에는 말풍선 설명이 함께 읽는다. */
@Composable
private fun AuthorName(message: ChatMessage) {
    Row(
        Modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            message.authorName,
            style = RunkkTextStyles.text(12, FontWeight.Bold, lineHeight = 14),
            color = RunkkColors.TextPrimary
        )
        if (message.isLeaderBadge) LeaderBadge()
    }
}

@Composable
private fun LeaderBadge() {
    val shape = RoundedCornerShape(8.dp)
    Text(
        stringResource(R.string.chat_leader_badge),
        style = RunkkTextStyles.text(10, lineHeight = 12),
        color = RunkkColors.TextPrimary,
        modifier = Modifier.border(1.dp, RunkkColors.Border, shape).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * 러닝 완료 시스템 메시지(피그마 61936:344, contract-W5): 배경·테두리 없이 가운데 두 줄(간격 2, 좌우 14·위아래 12). 「[닉네임]님이 러닝을
 * 마쳤어요」 12 Bold, 「5.02km」 11 Regular. 닉네임과 거리만 싣는다(W5-D7). 메뉴·반응 줄이 없다.
 */
@Composable
private fun SystemMessage(message: ChatMessage) {
    val title = stringResource(R.string.chat_run_finished_title, message.authorName)
    val distance = message.runDistance?.let { stringResource(R.string.chat_run_finished_distance, kmTwoDecimals(it)) }
    val description = listOfNotNull(title, distance).joinToString(", ")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = RunkkTextStyles.text(12, FontWeight.Bold, lineHeight = 14),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        if (distance != null) {
            Text(
                distance,
                style = RunkkTextStyles.text(11, lineHeight = 13),
                color = RunkkColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 말풍선과 시각, 길게 눌러 여는 메시지 메뉴. 보조 기술에는 "작성자, 시각, 내용" 한 문장과 길게 누르기 동작으로 읽힌다. */
@Composable
private fun MessageBody(message: ChatMessage, actions: ChatActions) {
    val author = if (message.isMine) stringResource(R.string.chat_me) else message.authorName
    val time = timeLabel(message.sentAt)
    val content = if (message.isRemoved) stringResource(R.string.chat_removed) else message.content
    val description = stringResource(R.string.chat_message_description, author, time, content)
    val menuLabel = stringResource(R.string.chat_menu)
    val hasMenu = !message.isRemoved && message.sendState == ChatMessage.SendState.SENT
    var isMenuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
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
            if (message.isMine) TimeText(time)
            Box(
                Modifier
                    .widthIn(max = 252.dp)
                    .clip(shape)
                    .background(if (message.isMine) RunkkColors.ChatMine else RunkkColors.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(content, style = RunkkTextStyles.text(13, lineHeight = 16), color = RunkkColors.TextPrimary)
            }
            if (!message.isMine) TimeText(time)
        }
        MessageMenu(message, isMenuOpen, onClose = { isMenuOpen = false }, actions = actions)
    }
}

/** 보낸 시각(10 Regular 보조색). */
@Composable
private fun TimeText(time: String) {
    Text(time, style = RunkkTextStyles.text(10, lineHeight = 12), color = RunkkColors.TextSecondary)
}

/**
 * 길게 누르면 열리는 메뉴(피그마 61943:10719): 폭 160, surface, 테두리 1, r16. 위 줄은 반응 세 가지(40 칸 r8, 아이콘 18, 아래 구분선),
 * 아래는 삭제(canDelete)·신고(남의 메시지, F-10). 내 메시지는 오른쪽 끝, 남의 메시지는 말풍선 왼쪽 끝에 맞춰 말풍선 위에 띄운다.
 */
@Composable
private fun MessageMenu(message: ChatMessage, isOpen: Boolean, onClose: () -> Unit, actions: ChatActions) {
    val author = if (message.isMine) stringResource(R.string.chat_me) else message.authorName
    val shape = RoundedCornerShape(16.dp)
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = onClose,
        offset = DpOffset(0.dp, MENU_LIFT),
        shape = shape,
        containerColor = RunkkColors.Surface,
        modifier = Modifier.width(160.dp).border(1.dp, RunkkColors.MenuDivider, shape)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ReactionKind.entries.forEach { kind ->
                val isOn = kind in message.myReactions
                val label = stringResource(
                    if (isOn) R.string.chat_reaction_remove else R.string.chat_reaction_add,
                    stringResource(kind.labelRes())
                )
                // 누름 칸 48 안에 피그마 40 칸을 그린다(접근성 최소 크기).
                Box(
                    Modifier
                        .size(48.dp)
                        .toggleable(value = isOn, role = Role.Checkbox, onValueChange = {
                            onClose()
                            actions.onReaction(message, kind)
                        })
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOn) RunkkColors.SurfaceHigh else RunkkColors.Surface)
                    )
                    Icon(
                        kind.icon(),
                        contentDescription = null,
                        tint = RunkkColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RunkkColors.MenuDivider))
        if (message.canDelete) {
            MenuItem(
                text = stringResource(R.string.chat_delete),
                description = stringResource(R.string.chat_delete_description, author),
                onClick = {
                    onClose()
                    actions.onDeleteAsk(message)
                }
            )
        }
        if (!message.isMine) {
            MenuItem(
                text = stringResource(R.string.chat_report),
                description = stringResource(R.string.chat_report_description, author),
                onClick = {
                    onClose()
                    actions.onReportAsk(message)
                }
            )
        }
    }
}

/** 메뉴 항목(높이 40, 좌우 12, 14 Regular). 누름 칸은 48이다. */
@Composable
private fun MenuItem(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, style = RunkkTextStyles.text(14, lineHeight = 17), color = RunkkColors.TextPrimary)
    }
}

/** 말풍선 아래: 보내는 중, 보내기 실패와 다시 보내기, 또는 수가 있는 반응 알약(간격 4). */
@Composable
private fun MessageFooter(message: ChatMessage, actions: ChatActions) {
    when {
        message.isRemoved -> Unit

        message.sendState == ChatMessage.SendState.SENDING -> Text(
            stringResource(R.string.chat_sending),
            style = RunkkTextStyles.Micro,
            color = RunkkColors.TextSecondary
        )

        message.sendState == ChatMessage.SendState.FAILED -> FailedRow(message, actions)

        else -> {
            val shown = ReactionKind.entries.filter { (message.reactions[it] ?: 0) > 0 }
            if (shown.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    shown.forEach { kind -> ReactionPill(message, kind, actions) }
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
            color = RunkkColors.Violet,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        RunkkTextLink(
            stringResource(R.string.chat_retry),
            onClick = { actions.onRetry(message) },
            contentDescription = stringResource(R.string.chat_retry_description, message.content.take(SNIPPET_CHARS))
        )
    }
}

/**
 * 반응 알약(피그마 61940:10080): 높이 24, r12, 좌8 우9, 간격 4, 줄선 아이콘 12.6 + 숫자 12 Medium. 비선택은 흰 바탕 + 테두리 1, 내가 단
 * 반응은 화면 인스턴스 값(textSecondary 채움, surfaceHigh 숫자). 누르면 달고 떼고, 길게 누르면 반응한 사람 시트를 그 종류 탭으로 연다.
 * 누름 칸은 48이다. 보조 기술에는 "좋아요 3개, 누름"과 길게 누르기 동작으로 읽힌다(FR-210).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactionPill(message: ChatMessage, kind: ReactionKind, actions: ChatActions) {
    val isOn = kind in message.myReactions
    val count = message.reactions[kind] ?: 0
    val name = stringResource(kind.labelRes())
    val description = stringResource(R.string.chat_reaction_description, name, count)
    val stateText = stringResource(if (isOn) R.string.chat_reaction_on else R.string.chat_reaction_off)
    val reactorsLabel = stringResource(R.string.chat_reactors_open, name)
    val shape = RoundedCornerShape(12.dp)
    val content = if (isOn) RunkkColors.SurfaceHigh else RunkkColors.TextPrimary
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .combinedClickable(
                role = Role.Checkbox,
                onClick = { actions.onReaction(message, kind) },
                onLongClickLabel = reactorsLabel,
                onLongClick = { actions.onOpenReactors(message, kind) }
            )
            .semantics {
                contentDescription = description
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .height(24.dp)
                .clip(shape)
                .background(if (isOn) RunkkColors.TextSecondary else RunkkColors.Surface)
                .border(1.dp, if (isOn) RunkkColors.TextSecondary else RunkkColors.Border, shape)
                .padding(start = 8.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(kind.icon(), contentDescription = null, tint = content, modifier = Modifier.size(12.6.dp))
            Text(
                count.toString(),
                style = RunkkTextStyles.text(12, FontWeight.Medium, lineHeight = 14),
                color = content
            )
        }
    }
}

/** "오후 6:10". 틀은 문자열 리소스(chat_time_pattern)다. */
@Composable
private fun timeLabel(at: Instant): String = at.atZone(ZoneId.systemDefault()).format(
    DateTimeFormatter.ofPattern(stringResource(R.string.chat_time_pattern), Locale.KOREAN)
)

/** "5.02"(소수 둘째 자리 버림, 서버 대체 문구와 같다). */
internal fun kmTwoDecimals(distance: Distance): String {
    val centi = distance.meters / METERS_PER_CENTI_KM
    return String.format(Locale.US, "%d.%02d", centi / CENTI_PER_KM, centi % CENTI_PER_KM)
}

/** 다시 보내기 단추 이름에 넣을 메시지 앞부분 글자 수. */
private const val SNIPPET_CHARS = 20

private const val METERS_PER_CENTI_KM = 10L
private const val CENTI_PER_KM = 100L

/** 메뉴를 앵커(말풍선) 아래가 아니라 말풍선 위로 올린다(피그마: 누른 말풍선 바로 위). */
private val MENU_LIFT = (-8).dp

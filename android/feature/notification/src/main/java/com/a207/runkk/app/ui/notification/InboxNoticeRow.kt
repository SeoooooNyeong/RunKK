package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.notification.Inbox

/** 운영자 공지 알림 종류(서버 NotificationType.NOTICE). */
internal const val NOTICE_TYPE = "NOTICE"

/**
 * 알림함 운영자 공지 한 줄(피그마 RunKK / 알림함 / 공지 행 62268:2807): 확성기 아이콘 원, 서버 공지 제목(없으면 「안내」, 데이터 계층이
 * 채움), 본문, 상대 시간, 링크가 있으면 바깥 열기 아이콘. 안 읽은 공지는 제목을 굵게 한다. 누르면 읽음 처리 뒤 링크를 바깥 브라우저로 연다.
 */
@Composable
internal fun InboxNoticeRow(item: Inbox.Item, time: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val readState = stringResource(if (item.isRead) R.string.notification_read else R.string.notification_unread)
    val hasLink = inboxLink(item.screen, item.targetId) != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = if (hasLink) stringResource(R.string.notification_open_link) else null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { stateDescription = readState },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(RunkkColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.Volume,
                contentDescription = null,
                tint = RunkkColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                style = RunkkTextStyles.text(15, if (item.isRead) FontWeight.Medium else FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
            if (item.body.isNotBlank()) {
                Text(item.body, style = RunkkTextStyles.Label, color = RunkkColors.TextPrimary)
            }
            Text(time, style = RunkkTextStyles.text(12), color = RunkkColors.TextSecondary)
        }
        if (hasLink) {
            Icon(
                RunkkIcons.OpenInNew,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

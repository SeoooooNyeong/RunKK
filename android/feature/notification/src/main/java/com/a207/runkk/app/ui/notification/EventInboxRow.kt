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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkNotificationDot
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 알림함 맨 위 이벤트 항목(피그마 RunKK / 알림함 / 이벤트 행 62268:2797): 선물 아이콘, "이벤트", "진행 중인 이벤트 n개",
 * 안 본 이벤트가 있으면 알림 점, 화살표.
 */
@Composable
internal fun EventInboxRow(summary: InboxEventSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val newEvent = stringResource(R.string.event_new)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (summary.hasUnseen) stateDescription = newEvent }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(RunkkColors.Violet),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.Gift,
                contentDescription = null,
                tint = RunkkColors.OnSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.event_inbox_title),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary
            )
            Text(
                stringResource(R.string.event_inbox_ongoing_count, summary.ongoingCount),
                style = RunkkTextStyles.Label,
                color = RunkkColors.TextSecondary
            )
        }
        if (summary.hasUnseen) RunkkNotificationDot()
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 홈 알림함 단추의 새 이벤트 점(칸 SlotKey.HOME_INBOX_DOT, 피그마 A-4 62273:7142). 홈이 다시 보일 때마다 다시 센다. 안 본 진행 중
 * 이벤트가 없으면 그리지 않는다. 자리(단추 원 오른쪽 위)는 홈이 정한다.
 */
@Composable
internal fun HomeInboxDot(viewModel: EventDotViewModel = hiltViewModel()) {
    val hasUnseen by viewModel.hasUnseen.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    if (hasUnseen) {
        val description = stringResource(R.string.event_new)
        RunkkNotificationDot(modifier = Modifier.semantics { contentDescription = description })
    }
}

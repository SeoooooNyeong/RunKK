package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/** 알림함 맨 위 이벤트 항목(피그마 62209:1274): 선물 아이콘, "이벤트", "진행 중인 이벤트 n개", 안 본 이벤트가 있으면 라임 점, 화살표. */
@Composable
internal fun EventInboxRow(summary: InboxEventSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val newEvent = stringResource(R.string.event_new)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
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
            Icon(RunkkIcons.Gift, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.event_inbox_title),
                style = RunkkTextStyles.text(16, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
            Text(
                stringResource(R.string.event_inbox_ongoing_count, summary.ongoingCount),
                style = RunkkTextStyles.text(13),
                color = RunkkColors.TextSecondary
            )
        }
        if (summary.hasUnseen) NewEventDot(size = 8.dp)
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 새 이벤트 라임 점. [ring]이 있으면 그 색 테두리(홈 알림함 단추 위, 피그마 62206:1008). */
@Composable
internal fun NewEventDot(size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(RunkkColors.LimeFill)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
    )
}

/**
 * 홈 알림함 단추의 새 이벤트 점(칸 SlotKey.HOME_INBOX_DOT). 홈이 다시 보일 때마다 다시 센다. 안 본 진행 중 이벤트가 없으면 그리지 않는다.
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
        NewEventDot(
            size = 12.dp,
            ring = RunkkColors.Surface,
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}

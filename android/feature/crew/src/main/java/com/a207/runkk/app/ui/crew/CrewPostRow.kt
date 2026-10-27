package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.crew.PostList

/**
 * 크루 글 머리줄: 종류 배지(있으면), 작성자·작성 시각, 삭제(canDelete: 본인 글, 크루장·운영진은 모든 글, FR-213)와
 * 신고(남의 글, FR-214). 두 단추의 보조 기술 이름에는 작성자를 넣어 어느 글인지 알린다.
 */
@Composable
internal fun PostHeader(
    item: PostList.Item,
    onDeleteAsk: (PostList.Item) -> Unit,
    onReport: (PostList.Item) -> Unit,
    badge: String? = null,
    hasTrailing: Boolean = false,
    trailing: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val snippet = item.summary.take(SNIPPET_CHARS)
    // 글자를 키워도 작성자 글이 눌리지 않게 머리줄과 행동 줄을 나눈다(WCAG 1.4.4, 1.4.10).
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            badge?.let { RunkkBadge(it, tone = RunkkBadgeTone.Lime) }
            Text(
                stringResource(R.string.crew_home_run_author_time, item.authorName, postedLabel(item.writtenAt)),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        }
        val hasActions = item.canDelete || !item.isMine || hasTrailing
        if (hasActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailing()
                if (item.canDelete) {
                    RunkkTextLink(
                        stringResource(R.string.crew_action_delete),
                        onClick = { onDeleteAsk(item) },
                        contentDescription = stringResource(R.string.crew_delete_description, item.authorName, snippet)
                    )
                }
                if (!item.isMine) {
                    RunkkTextLink(
                        stringResource(R.string.crew_report),
                        onClick = { onReport(item) },
                        contentDescription = stringResource(R.string.crew_report_description, item.authorName, snippet)
                    )
                }
            }
        }
    }
}

/** 삭제·신고 단추 이름에 넣을 글 앞부분 글자 수(같은 작성자의 글을 구분한다, WCAG 2.4.4). */
private const val SNIPPET_CHARS = 20

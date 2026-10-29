package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 안내 카드 대화상자(A-3 게스트 안내, A-6 위치 동의, 잠긴 기능 안내): 둥근 표면 카드에 제목, [content], 아래 줄의
 * 글자 버튼([dismissLabel], 있으면)과 라임 알약 버튼([confirmLabel]). 바깥 누름·뒤로가기는 [onDismissRequest]다.
 */
@Composable
fun RunkkActionDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(RunkkColors.Surface)
                .semantics { paneTitle = title }
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            content()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dismissLabel != null) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(dismissLabel, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextSecondary)
                    }
                }
                RunkkLimePillButton(
                    confirmLabel,
                    onClick = onConfirm,
                    modifier = if (dismissLabel != null) Modifier.weight(1f) else Modifier
                )
            }
        }
    }
}

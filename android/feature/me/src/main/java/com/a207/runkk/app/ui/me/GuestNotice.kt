package com.a207.runkk.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 게스트 시작 직후 안내(A8, FR-199): 자동으로 붙은 닉네임과 자료 유실 고지. "나중에"([onConfirm])면 홈으로,
 * "지금 연동하기"([onLink])면 소셜 계정 연동(F9)으로. 바깥을 눌러도 "나중에"와 같다. [nickname]을 아직 못 읽었으면 닉네임 칸을 뺀다.
 */
@Composable
internal fun GuestNoticeDialog(nickname: String?, onConfirm: () -> Unit, onLink: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        containerColor = RunkkColors.Surface,
        titleContentColor = RunkkColors.TextPrimary,
        textContentColor = RunkkColors.TextSecondary,
        title = { Text(stringResource(R.string.me_guest_notice_title), style = RunkkTextStyles.CardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                nickname?.let { NicknameBox(it) }
                Text(stringResource(R.string.me_guest_notice_message), style = RunkkTextStyles.Body)
            }
        },
        confirmButton = {
            TextButton(onClick = onLink) {
                Text(
                    stringResource(R.string.me_guest_notice_link),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lime
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.me_guest_notice_later),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    )
}

/** "내 닉네임" 칸. */
@Composable
private fun NicknameBox(nickname: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RunkkColors.Background, RunkkShapes.Medium)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            stringResource(R.string.me_guest_notice_nickname),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        Text(nickname, style = RunkkTextStyles.text(17, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

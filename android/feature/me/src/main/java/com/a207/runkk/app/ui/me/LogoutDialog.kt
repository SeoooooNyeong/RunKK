package com.a207.runkk.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 로그아웃 확인(FR-002). 연동하지 않은 게스트면(F10, FR-199) 기기 기록이 지워진다는 한 줄과 "먼저 연동하기"(F9)를 함께 보인다.
 * 게스트 로그아웃은 기기의 미전송 기록도 지운다(LogoutUseCase).
 */
@Composable
internal fun LogoutDialog(isGuest: Boolean, onConfirm: () -> Unit, onLink: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RunkkColors.Surface,
        titleContentColor = RunkkColors.TextPrimary,
        textContentColor = RunkkColors.TextSecondary,
        title = { Text(stringResource(R.string.me_logout_title), style = RunkkTextStyles.CardTitle) },
        text = if (isGuest) {
            { GuestLogoutBody(onLink) }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.me_logout_confirm),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lavender
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.me_logout_cancel),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    )
}

/** 게스트 로그아웃 본문 한 줄과 "먼저 연동하기". */
@Composable
private fun GuestLogoutBody(onLink: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.me_logout_guest_message), style = RunkkTextStyles.Body)
        TextButton(onClick = onLink) {
            Text(
                stringResource(R.string.me_logout_link_first),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.Lime
            )
        }
    }
}

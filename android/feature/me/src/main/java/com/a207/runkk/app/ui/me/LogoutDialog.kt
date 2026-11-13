package com.a207.runkk.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkDialogButtons
import com.a207.runkk.app.core.designsystem.RunkkDialogMessage

/**
 * 로그아웃 확인(FR-002). 연동하지 않은 게스트면(F10, FR-199) 기기 기록이 지워진다는 한 줄과 "먼저 연동하기"(F9)를 함께 보인다.
 * 게스트 로그아웃은 기기의 미전송 기록도 지운다(LogoutUseCase).
 */
@Composable
internal fun LogoutDialog(isGuest: Boolean, onConfirm: () -> Unit, onLink: () -> Unit, onDismiss: () -> Unit) {
    RunkkDialog(title = stringResource(R.string.me_logout_title), onDismissRequest = onDismiss) {
        if (isGuest) GuestLogoutBody(onLink)
        RunkkDialogButtons(
            confirmLabel = stringResource(R.string.me_logout_confirm),
            onConfirm = onConfirm,
            dismissLabel = stringResource(R.string.me_logout_cancel),
            onDismiss = onDismiss
        )
    }
}

/** 게스트 로그아웃 본문 한 줄과 "먼저 연동하기". */
@Composable
private fun GuestLogoutBody(onLink: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RunkkDialogMessage(stringResource(R.string.me_logout_guest_message))
        RunkkButton(
            stringResource(R.string.me_logout_link_first),
            onClick = onLink,
            style = RunkkButtonStyle.Text,
            size = RunkkButtonSize.Small
        )
    }
}

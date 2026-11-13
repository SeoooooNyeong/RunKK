package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkDialogMessage

/**
 * 크루런 그만두기 확인(피그마 F-15d): 공통 대화상자(RunkkDialog: 모서리 28, 제목 18 Bold, 덮개 scrim)에 안내, 아래 Medium 48 알약 둘
 * (외곽선 「계속 달리기」 고정 143, 보라 「그만두기」 나머지 너비). 바깥 누름·뒤로 가기는 계속 달리기다.
 */
@Composable
internal fun CrewQuitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RunkkDialog(title = stringResource(R.string.crew_run_quit_confirm_title), onDismissRequest = onDismiss) {
        RunkkDialogMessage(stringResource(R.string.crew_run_quit_confirm_message))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RunkkButton(
                stringResource(R.string.crew_run_quit_confirm_dismiss),
                onClick = onDismiss,
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium,
                modifier = Modifier.width(KEEP_RUNNING_WIDTH)
            )
            RunkkButton(
                stringResource(R.string.crew_run_quit_confirm_action),
                onClick = onConfirm,
                style = RunkkButtonStyle.Purple,
                size = RunkkButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 「계속 달리기」 고정 너비(피그마 F-15d). */
private val KEEP_RUNNING_WIDTH = 143.dp

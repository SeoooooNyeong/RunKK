package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

/**
 * 크루런 그만두기 확인(피그마 F-15d): 둥근 표면 카드에 제목·안내, 아래 알약 단추 둘(외곽선 "계속 달리기", 보라 채움 "그만두기").
 * 디자인 시스템 알약(RunkkPillButton)은 높이 40에 글자 너비라 반씩 나눈 48 높이 알약을 이 화면에 둔다. 바깥 누름·뒤로 가기는 계속 달리기다.
 */
@Composable
internal fun CrewQuitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.crew_run_quit_confirm_title)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(RunkkColors.Surface)
                .semantics { paneTitle = title }
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_run_quit_confirm_message),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextPrimary
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuitPill(
                    stringResource(R.string.crew_run_quit_confirm_dismiss),
                    onClick = onDismiss,
                    background = RunkkColors.Surface,
                    content = RunkkColors.TextPrimary,
                    border = RunkkColors.BorderStrong,
                    modifier = Modifier.weight(1f)
                )
                QuitPill(
                    stringResource(R.string.crew_run_quit_confirm_action),
                    onClick = onConfirm,
                    background = RunkkColors.Violet,
                    content = Color.White,
                    border = null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 48 높이 알약 단추 한 칸. */
@Composable
private fun QuitPill(
    label: String,
    onClick: () -> Unit,
    background: Color,
    content: Color,
    border: Color?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = RunkkTextStyles.text(15, FontWeight.Bold), color = content)
    }
}

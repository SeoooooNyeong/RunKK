package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 대화상자 틀(피그마 확인 대화상자, S15P31A207-292): 화면 가운데에 양옆 28을 띄운 카드(폭 334 기준, 모서리 28, 안쪽 위 24·좌우 20·
 * 아래 20, 줄 간격 10)에 제목(18/26 Bold)과 [content]를 놓는다. 뒤 딤은 45%다(피그마 rgba(23,27,18,0.45), 창 딤은 검정이라 근사).
 * 바깥 누름·뒤로가기는 [onDismissRequest]다. 버튼 줄은 [RunkkDialogButtons]로 만든다.
 *
 * ```
 * RunkkDialog(title = "기록을 지울까요?", onDismissRequest = onDismiss) {
 *     RunkkDialogMessage("지운 기록은 되돌릴 수 없어요.")
 *     RunkkDialogButtons(confirmLabel = "지우기", onConfirm = onDelete, dismissLabel = "취소", onDismiss = onDismiss)
 * }
 * ```
 */
@Composable
fun RunkkDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(DIALOG_DIM) }
        Column(
            modifier = modifier
                .padding(horizontal = DIALOG_SIDE)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .clip(RunkkShapes.Dialog)
                .background(RunkkColors.Surface)
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = RunkkTextStyles.DialogTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

/** 대화상자 본문(피그마 14/21 Regular, 본문색). */
@Composable
fun RunkkDialogMessage(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DIALOG_BODY, color = RunkkColors.TextPrimary, modifier = modifier)
}

/**
 * 대화상자 아래 버튼 줄(위 여백 10, 간격 10, 높이 48 알약). [dismissLabel]이 있으면 왼쪽에 흰 외곽선 버튼, 오른쪽에 [confirmStyle]
 * 확인 버튼을 같은 폭으로 놓고, 없으면 확인 버튼 하나를 폭 가득 놓는다.
 */
@Composable
fun RunkkDialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
    confirmStyle: RunkkButtonStyle = RunkkButtonStyle.Purple,
    isConfirmEnabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (dismissLabel != null) {
            RunkkButton(
                dismissLabel,
                onClick = onDismiss,
                style = RunkkButtonStyle.Outline,
                size = RunkkButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
        }
        RunkkButton(
            confirmLabel,
            onClick = onConfirm,
            style = confirmStyle,
            size = RunkkButtonSize.Medium,
            isEnabled = isConfirmEnabled,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 안내 카드 대화상자(A-3 게스트 안내, A-6 위치 동의, 잠긴 기능 안내): [RunkkDialog] 틀에 제목, [content], 아래 줄의
 * 흰 외곽선 버튼([dismissLabel], 있으면)과 라임 알약 버튼([confirmLabel], 피그마 안내 대화상자는 라임 하나). 바깥 누름·뒤로가기는
 * [onDismissRequest]다.
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
    RunkkDialog(title = title, onDismissRequest = onDismissRequest, modifier = modifier) {
        content()
        RunkkDialogButtons(
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            dismissLabel = dismissLabel,
            onDismiss = onDismiss,
            confirmStyle = RunkkButtonStyle.Lime
        )
    }
}

/** 대화상자 카드 양옆 여백(390 화면에서 폭 334). */
private val DIALOG_SIDE = 28.dp

/** 큰 화면에서도 카드가 너무 넓어지지 않게(피그마 폭 334에 여유). */
private val DIALOG_MAX_WIDTH = 400.dp

/** 대화상자 뒤 딤(피그마 0.45). */
private const val DIALOG_DIM = 0.45f

private val DIALOG_BODY = RunkkTextStyles.text(14, lineHeight = 21)

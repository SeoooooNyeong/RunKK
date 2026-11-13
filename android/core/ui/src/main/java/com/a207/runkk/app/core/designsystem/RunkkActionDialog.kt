package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
 * [onClose]가 있으면 카드 오른쪽 위에 닫기 ✕([closeLabel])를 단다(크루 초대 코드·크루원 정보). [borderColor]는 카드 테두리(1dp)다.
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
    onClose: (() -> Unit)? = null,
    closeLabel: String = stringResource(R.string.core_sheet_close),
    borderColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(DIALOG_DIM) }
        Box(
            modifier = modifier
                .padding(horizontal = DIALOG_SIDE)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .clip(RunkkShapes.Dialog)
                .background(RunkkColors.Surface)
                .then(if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, RunkkShapes.Dialog) else Modifier)
                .semantics { paneTitle = title }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = RunkkTextStyles.DialogTitle,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier
                        .then(if (onClose != null) Modifier.padding(end = CLOSE_TITLE_GAP) else Modifier)
                        .semantics { heading() }
                )
                content()
            }
            if (onClose != null) DialogCloseButton(closeLabel, onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** 대화상자 오른쪽 위 닫기 ✕(피그마 F-18b·F-22b: 카드 모서리에서 4 띄운 48 누름 칸, 아이콘 20). */
@Composable
private fun DialogCloseButton(label: String, onClose: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClose)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            RunkkIcons.Close,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
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

/** 닫기 ✕가 있을 때 제목이 ✕ 아래로 들어가지 않게 띄우는 오른쪽 여백. */
private val CLOSE_TITLE_GAP = 32.dp

private val DIALOG_BODY = RunkkTextStyles.text(14, lineHeight = 21)

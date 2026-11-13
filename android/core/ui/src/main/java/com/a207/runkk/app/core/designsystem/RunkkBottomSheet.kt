package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 바텀시트(피그마 Bottom Sheet, S15P31A207-292): 표면 바탕, 위 모서리 28, 드래그 손잡이 없음, 안쪽 위 24·좌우 20·아래 32, 줄 간격 20.
 * [title]이 있으면 머리에 제목(18/22 Bold)과 오른쪽 닫기 ✕를 놓는다. ✕와 바깥 누름·뒤로가기·아래로 끌기는 모두 [onDismissRequest]다.
 * [hasDragHandle]이면 맨 위에 36×4 손잡이를 두고(대결 입장 시트, 피그마 61355:3402) 안쪽 위 여백을 12로 줄인다. [hasCloseButton]이
 * 거짓이면 머리에 ✕를 두지 않는다.
 * 뒤 딤은 [RunkkColors.Scrim](피그마 rgba(23,27,18,0.45))이다.
 *
 * ```
 * RunkkBottomSheet(onDismissRequest = close, title = "조건 설정하기") {
 *     // 내용
 *     RunkkButton("적용하기", onClick = apply, size = RunkkButtonSize.Medium, modifier = Modifier.fillMaxWidth())
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunkkBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    hasDragHandle: Boolean = false,
    hasCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // 시트는 늘 다 펼친다(피그마 시트는 반만 열린 상태가 없다). 상태 타입이 실험 API라 바깥에 내보내지 않는다.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RunkkShapes.Sheet,
        containerColor = RunkkColors.Surface,
        contentColor = RunkkColors.TextPrimary,
        scrimColor = RunkkColors.Scrim,
        dragHandle = null
    ) {
        if (hasDragHandle) SheetDragHandle()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = if (hasDragHandle) 12.dp else 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (title != null) SheetHeader(title, onClose = if (hasCloseButton) onDismissRequest else null)
            content()
        }
    }
}

/** 시트 머리: 제목(18/22 Bold) + 오른쪽 닫기 ✕(18, 누름 칸 48). [onClose]가 null이면 ✕ 없이 제목만 둔다. */
@Composable
private fun SheetHeader(title: String, onClose: (() -> Unit)?) {
    val closeLabel = stringResource(R.string.core_sheet_close)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = RunkkTextStyles.SheetTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (onClose != null) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    RunkkIcons.Close,
                    contentDescription = null,
                    tint = RunkkColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 드래그 손잡이(피그마 61355:3402 대결 입장 시트: 36×4 divider 알약, 위 10). 장식이라 읽지 않는다. */
@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag(SHEET_DRAG_HANDLE_TAG),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(RunkkColors.Border)
                .clearAndSetSemantics {}
        )
    }
}

/** 드래그 손잡이의 시험 표지. */
internal const val SHEET_DRAG_HANDLE_TAG = "runkk-sheet-drag-handle"

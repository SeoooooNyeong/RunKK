package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 신고 시트(크루 콘텐츠·채팅 메시지, FR-214, 와이어프레임 M10). 아래에서 올라오는 시트에 제목과 대상 한 줄, 사유 하나(라디오),
 * 신고 내용과 글자 수, 취소·신고하기를 둔다. 사유 라벨과 공용 문구는 core.ui가 갖고([ReportReasonLabels], [reportDialogTexts])
 * 화면은 제목·대상·상세 칸 라벨과 [state]만 정한다(D-186, AN5-D-3·AN5-D-9). 색은 MaterialTheme에서 읽는다(NFR-041).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunkkReportDialog(
    texts: ReportDialogTexts,
    state: ReportDialogState,
    onSelect: (Int) -> Unit,
    onDetail: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        RunkkReportSheetContent(texts, state, ReportSheetCallbacks(onSelect, onDetail, onSubmit, onDismiss))
    }
}

/** 시트 안 내용. 시트 없이도 그린다(스크린샷 시험). */
@Composable
fun RunkkReportSheetContent(
    texts: ReportDialogTexts,
    state: ReportDialogState,
    callbacks: ReportSheetCallbacks,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val reasons = ReportReasonLabels.labels()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                texts.title,
                style = RunkkTextStyles.CardTitle,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            texts.subtitle?.let { Text(it, style = RunkkTextStyles.Caption, color = colors.onSurfaceVariant) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(texts.reasonsLabel, style = RunkkTextStyles.Caption, color = colors.onSurfaceVariant)
            Column(Modifier.selectableGroup().semantics { contentDescription = texts.reasonsLabel }) {
                reasons.forEachIndexed { index, label ->
                    ReasonRow(label, isSelected = index == state.selectedIndex) { callbacks.onSelect(index) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RunkkTextField(
                value = state.detail,
                onValueChange = callbacks.onDetail,
                label = texts.detailLabel,
                isSingleLine = false
            )
            Text(
                "${state.detail.codePointLength()}/${state.detailMax}",
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkSecondaryButton(texts.dismissLabel, onClick = callbacks.onDismiss, modifier = Modifier.weight(1f))
            RunkkPrimaryButton(
                texts.submitLabel,
                onClick = callbacks.onSubmit,
                isEnabled = state.isSubmitEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 사유 한 줄: 줄 전체가 라디오 단추로 눌린다(터치 영역 48dp). */
@Composable
private fun ReasonRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline)
        )
        Text(
            label,
            style = if (isSelected) RunkkTextStyles.BodyStrong else RunkkTextStyles.Body,
            color = colors.onSurface
        )
    }
}

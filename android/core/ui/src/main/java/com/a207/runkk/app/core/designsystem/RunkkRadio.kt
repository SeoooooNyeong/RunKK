package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 라디오 표시(피그마 F-12f): 22 원. 안 고르면 [RunkkColors.RadioStroke] 1.5 테두리, 고르면 [RunkkColors.RadioSelectedOuter] 2 테두리와
 * 가운데 10 [RunkkColors.RadioDot] 점. 장식이라 읽지 않는다(고름 상태는 줄이 알린다).
 */
@Composable
fun RunkkRadioMark(isSelected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(RADIO_MARK_TAG)
            .size(RADIO_SIZE)
            .clip(CircleShape)
            .border(
                if (isSelected) 2.dp else 1.5.dp,
                if (isSelected) RunkkColors.RadioSelectedOuter else RunkkColors.RadioStroke,
                CircleShape
            )
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) Box(Modifier.size(RADIO_DOT).clip(CircleShape).background(RunkkColors.RadioDot))
    }
}

/**
 * 라디오 한 줄(피그마 F-12f 반복 선택): 높이 52 이상, 글자 16/19 Regular, 아래 보조 문구(선택, 13 보조색), 오른쪽 [RunkkRadioMark].
 * 줄 전체가 눌리고 보조 기술에는 라디오 단추로 읽힌다. 묶음에는 `Modifier.selectableGroup()`을 건다. [hasDivider]면 아래 divider를 긋는다.
 */
@Composable
fun RunkkRadioRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    minHeight: Dp = 52.dp,
    labelStyle: TextStyle = RADIO_LABEL,
    isEnabled: Boolean = true,
    hasDivider: Boolean = false
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .selectable(selected = isSelected, enabled = isEnabled, role = Role.RadioButton, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = labelStyle, color = RunkkColors.TextPrimary)
                if (supporting != null) {
                    Text(supporting, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary)
                }
            }
            RunkkRadioMark(isSelected)
        }
        if (hasDivider) HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)
    }
}

/** 라디오 표시의 시험 표지. */
internal const val RADIO_MARK_TAG = "runkk-radio-mark"

private val RADIO_SIZE = 22.dp
private val RADIO_DOT = 10.dp
private val RADIO_LABEL = RunkkTextStyles.text(16, lineHeight = 19)

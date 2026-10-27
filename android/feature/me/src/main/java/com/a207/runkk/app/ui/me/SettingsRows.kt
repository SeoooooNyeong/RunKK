package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkSegmentOption
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkTextStyles

// 설정 화면들이 함께 쓰는 줄 모양(묶음 카드, 선택 줄, 값 줄, 고르기 대화상자).

/** 묶음 제목 + 카드 하나. */
@Composable
internal fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSectionTitle(stringResource(title), modifier = Modifier.padding(horizontal = 8.dp))
        RunkkCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}

@Composable
internal fun SettingsDivider() = HorizontalDivider(color = RunkkColors.Border)

/** 제목 아래 하나만 고르는 선택지(자유런 설정). */
@Composable
internal fun ChoiceSetting(
    @StringRes title: Int,
    @StringRes description: Int?,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(title),
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary
        )
        if (description != null) {
            Text(stringResource(description), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        RunkkSegmentRow(
            options = options.map { stringResource(it.second) },
            selectedIndex = options.indexOfFirst { it.first == selected },
            onSelect = { onSelect(options[it].first) }
        )
    }
}

/** 제목 오른쪽에 작은 선택지를 나란히 두는 줄(화면 테마, 거리 단위). [optionWidth]는 선택지 하나의 너비. */
@Composable
internal fun InlineChoiceRow(
    @StringRes title: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    optionWidth: Dp,
    onSelect: (String) -> Unit
) {
    val titleText = stringResource(title)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            titleText,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.selectableGroup().semantics { contentDescription = titleText },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                RunkkSegmentOption(
                    label = stringResource(label),
                    isSelected = value == selected,
                    onClick = { onSelect(value) },
                    modifier = Modifier.width(optionWidth),
                    height = 32.dp
                )
            }
        }
    }
}

/** 제목과 값(오른쪽, 보조색) 줄. [onClick]이 있으면 누르는 줄이고 화살표를 붙인다. */
@Composable
internal fun ValueRow(title: String, value: String?, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        value?.let { Text(it, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary) }
        if (onClick != null) {
            Icon(
                RunkkIcons.ChevronRight,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 하나만 고르는 대화상자(기록 공개 범위). 고르면 바로 닫는다. */
@Composable
internal fun ChoiceDialog(
    @StringRes title: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RunkkColors.Surface,
        title = { Text(stringResource(title), style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(label),
                            style = RunkkTextStyles.text(15, if (isSelected) FontWeight.Bold else FontWeight.Normal),
                            color = if (isSelected) RunkkColors.Lime else RunkkColors.TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.me_legal_close), color = RunkkColors.TextSecondary)
            }
        }
    )
}

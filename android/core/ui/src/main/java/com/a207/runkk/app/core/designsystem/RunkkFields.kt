package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 입력칸(높이 48, 모서리 12, 표면 + 강한 테두리). 위에 [label](12, 보조색), 아래에 [error] 또는 [supporting].
 * [maxLength]를 넘는 입력은 받지 않는다. [onImeAction]이 있으면 키보드의 [imeAction] 단추(검색·완료)를 그 동작으로 받는다.
 */
@Composable
fun RunkkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int? = null,
    supporting: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    val shape = RunkkShapes.Small
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        BasicTextField(
            value = value,
            onValueChange = { next -> if (maxLength == null || next.length <= maxLength) onValueChange(next) },
            singleLine = singleLine,
            textStyle = RunkkTextStyles.text(16).copy(color = RunkkColors.TextPrimary),
            cursorBrush = SolidColor(RunkkColors.Lime),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            // 완료·검색 같은 키보드 동작을 화면이 받는다. 없으면 기본 동작(다음 칸으로 이동 등).
            keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) }
                ?: KeyboardActions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (error != null) error(error)
                },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(RunkkColors.Surface)
                        .border(1.dp, if (error != null) RunkkColors.Lavender else RunkkColors.InputBorder, shape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = RunkkTextStyles.text(16), color = RunkkColors.TextSecondary)
                    }
                    inner()
                }
            }
        )
        when {
            error != null -> Text(
                text = error,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            supporting != null -> Text(
                text = supporting,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 고르기 칸(출생연도·성별): 입력칸 모양을 누르면 목록이 열린다. [selected]가 null이면 "선택". */
@Composable
fun <T> RunkkSelectField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "선택"
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RunkkShapes.Small
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(RunkkColors.Surface)
                    .border(1.dp, RunkkColors.InputBorder, shape)
                    .clickable(role = Role.DropdownList, onClickLabel = "$label 고르기") { expanded = true }
                    .semantics {
                        contentDescription = label
                        stateDescription =
                            selected?.let(optionLabel) ?: placeholder
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected?.let(optionLabel) ?: placeholder,
                    style = RunkkTextStyles.text(15),
                    color = if (selected == null) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    RunkkIcons.ChevronRight,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = {
                expanded = false
            }, modifier = Modifier.heightIn(max = 320.dp).background(RunkkColors.Surface)) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(optionLabel(option), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/** 켜고 끄는 설정 한 줄: 제목 + 설명(선택) + 라임 스위치. 줄 전체가 눌린다(터치 영역). */
@Composable
fun RunkkSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = RunkkTextStyles.text(15, FontWeight.Medium), color = RunkkColors.TextPrimary)
            if (description !=
                null
            ) {
                Text(text = description, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RunkkColors.OnLime,
                checkedTrackColor = RunkkColors.Lime,
                uncheckedThumbColor = RunkkColors.TextSecondary,
                uncheckedTrackColor = RunkkColors.Border,
                uncheckedBorderColor = RunkkColors.BorderStrong
            )
        )
    }
}

/**
 * 체크 한 줄(약관 동의): [tag]("[필수]" 라임 / "[선택]" 보조색) + 문구 + 오른쪽 "보기"(선택).
 * [highlighted]면 "전체 동의"처럼 라임 테두리 상자로 그린다.
 */
@Composable
fun RunkkCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    tagHighlighted: Boolean = false,
    highlighted: Boolean = false,
    onView: (() -> Unit)? = null
) {
    val shape = RunkkShapes.Medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.clip(shape).background(RunkkColors.LimeContainer).border(1.dp, RunkkColors.Lime, shape)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(horizontal = if (highlighted) 16.dp else 0.dp, vertical = if (highlighted) 12.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = RunkkColors.Lime,
                    checkmarkColor = RunkkColors.OnLime,
                    uncheckedColor = RunkkColors.InputBorder
                )
            )
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    if (tag != null) {
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = if (tagHighlighted) RunkkColors.Lime else RunkkColors.TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        append(tag)
                        pop()
                        append(" ")
                    }
                    append(label)
                },
                style = if (highlighted) RunkkTextStyles.text(16, FontWeight.Black) else RunkkTextStyles.Body,
                color = RunkkColors.TextPrimary
            )
        }
        if (onView != null) RunkkTextLink(text = "보기", onClick = onView, contentDescription = "$label 보기")
    }
}

/**
 * 확인 대화상자. [destructive]면 확인 글자를 라벤더로(탈퇴·로그아웃·동의 철회).
 * [onDismiss]는 두 번째 버튼, [onDismissRequest]는 뒤로가기·바깥 누름이다. 두 번째 버튼이 "취소"가 아니라 다른 행동이면
 * (예: "동의 관리"로 이동) [onDismissRequest]를 따로 넘겨 대화상자만 닫는다.
 */
@Composable
fun RunkkConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "취소",
    destructive: Boolean = false,
    onDismissRequest: () -> Unit = onDismiss
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = RunkkColors.Surface,
        titleContentColor = RunkkColors.TextPrimary,
        textContentColor = RunkkColors.TextSecondary,
        title = { Text(title, style = RunkkTextStyles.CardTitle) },
        text = { Text(message, style = RunkkTextStyles.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = RunkkTextStyles.BodyStrong,
                    color = if (destructive) RunkkColors.Lavender else RunkkColors.Lime
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextSecondary)
            }
        }
    )
}

/** 안내 문구 띄우기(스낵바, 표면색). */
@Composable
fun RunkkSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = RunkkColors.Border,
            contentColor = RunkkColors.TextPrimary,
            actionColor = RunkkColors.Lime,
            shape = RunkkShapes.Medium
        )
    }
}

@Preview(name = "입력·설정", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkFieldsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkTextField(value = "", onValueChange = {}, label = "닉네임 · 2~20자, 비우면 계정 이름", placeholder = "닉네임")
            RunkkTextField(value = "가", onValueChange = {}, label = "닉네임", error = "2자 이상 입력해 주세요")
            RunkkSelectField(label = "출생연도 (선택)", options = listOf(1990, 1991), selected = null, optionLabel = {
                "${it}년"
            }, onSelect = {})
            RunkkCheckRow(label = "전체 동의", checked = true, onCheckedChange = {}, highlighted = true)
            RunkkCheckRow(label = "서비스 이용약관", checked = true, onCheckedChange = {
            }, tag = "[필수]", tagHighlighted = true, onView = {})
            RunkkSwitchRow(title = "음성 안내", description = "1km마다 알려 줘요", checked = true, onCheckedChange = {})
        }
    }
}

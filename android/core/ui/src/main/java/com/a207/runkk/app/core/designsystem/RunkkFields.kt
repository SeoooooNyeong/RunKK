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
import androidx.compose.ui.res.stringResource
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
    isSingleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { next -> if (maxLength == null || next.length <= maxLength) onValueChange(next) },
            singleLine = isSingleLine,
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
            // 라벨을 입력칸 장식 안에 두어 입력칸 노드가 라벨을 이름으로 읽게 한다(값이 있어도 "라벨, 값, 편집 상자").
            decorationBox = { inner ->
                RunkkTextFieldDecoration(label, value, placeholder, error != null, inner)
            }
        )
        RunkkFieldFootnote(error, supporting)
    }
}

/** 입력칸 장식: 위 라벨, 테두리 상자, 비었을 때 자리 문구. */
@Composable
private fun RunkkTextFieldDecoration(
    label: String,
    value: String,
    placeholder: String?,
    hasError: Boolean,
    inner: @Composable () -> Unit
) {
    val shape = RunkkShapes.Small
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(RunkkColors.Surface)
                .border(1.dp, if (hasError) RunkkColors.Lavender else RunkkColors.InputBorder, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(text = placeholder, style = RunkkTextStyles.text(16), color = RunkkColors.TextSecondary)
            }
            inner()
        }
    }
}

/** 입력칸 아래 줄: 오류가 우선, 없으면 보조 문구. */
@Composable
private fun RunkkFieldFootnote(error: String?, supporting: String?) {
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

/** 고르기 칸(출생연도·성별): 입력칸 모양을 누르면 목록이 열린다. [selected]가 null이면 "선택". */
@Composable
fun <T> RunkkSelectField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.core_fields_select)
) {
    var expanded by remember { mutableStateOf(false) }
    val selectActionLabel = stringResource(R.string.core_fields_select_action, label)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Box {
            RunkkSelectBox(label, selected?.let(optionLabel), placeholder, selectActionLabel) { expanded = true }
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
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    isEnabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = isChecked, enabled = isEnabled, role = Role.Switch, onValueChange = onCheckedChange)
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
            checked = isChecked,
            onCheckedChange = null,
            enabled = isEnabled,
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

/** 고르기 칸의 입력칸 모양 줄: 고른 값(없으면 [placeholder])과 화살표. */
@Composable
private fun RunkkSelectBox(
    label: String,
    selectedText: String?,
    placeholder: String,
    selectActionLabel: String,
    onOpen: () -> Unit
) {
    val shape = RunkkShapes.Small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.InputBorder, shape)
            .clickable(role = Role.DropdownList, onClickLabel = selectActionLabel, onClick = onOpen)
            .semantics {
                contentDescription = label
                stateDescription = selectedText ?: placeholder
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selectedText ?: placeholder,
            style = RunkkTextStyles.text(15),
            color = if (selectedText == null) RunkkColors.TextSecondary else RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 체크 한 줄(약관 동의): [tag]("[필수]" 라임 / "[선택]" 보조색) + 문구 + 오른쪽 "보기"(선택).
 * [isHighlighted]면 "전체 동의"처럼 라임 테두리 상자로 그린다.
 */
@Composable
fun RunkkCheckRow(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    isTagHighlighted: Boolean = false,
    isHighlighted: Boolean = false,
    onView: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().then(checkRowBox(isHighlighted)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(
                    horizontal = if (isHighlighted) 16.dp else 0.dp,
                    vertical = if (isHighlighted) 12.dp else 0.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = RunkkColors.Lime,
                    checkmarkColor = RunkkColors.OnLime,
                    uncheckedColor = RunkkColors.InputBorder
                )
            )
            RunkkCheckRowLabel(label, tag, isTagHighlighted, isHighlighted)
        }
        if (onView != null) {
            RunkkTextLink(
                text = stringResource(R.string.core_fields_view),
                onClick = onView,
                contentDescription = stringResource(R.string.core_fields_view_label, label)
            )
        }
    }
}

/** 강조 체크 줄("전체 동의")은 라임 테두리 상자로 그린다. */
private fun checkRowBox(isHighlighted: Boolean): Modifier {
    val shape = RunkkShapes.Medium
    return if (isHighlighted) {
        Modifier.clip(shape).background(RunkkColors.LimeContainer).border(1.dp, RunkkColors.Lime, shape)
    } else {
        Modifier
    }
}

/** 체크 한 줄의 문구: 앞에 [tag]("[필수]" 라임 / "[선택]" 보조색)가 있으면 굵게 붙인다. */
@Composable
private fun RunkkCheckRowLabel(label: String, tag: String?, isTagHighlighted: Boolean, isHighlighted: Boolean) {
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            if (tag != null) {
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = if (isTagHighlighted) RunkkColors.Lime else RunkkColors.TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                append(tag)
                pop()
                append(" ")
            }
            append(label)
        },
        style = if (isHighlighted) RunkkTextStyles.text(16, FontWeight.Black) else RunkkTextStyles.Body,
        color = RunkkColors.TextPrimary
    )
}

/**
 * 확인 대화상자. [isDestructive]면 확인 글자를 라벤더로(탈퇴·로그아웃·동의 철회).
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
    dismissLabel: String = stringResource(R.string.core_fields_cancel),
    isDestructive: Boolean = false,
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
                    color = if (isDestructive) RunkkColors.Lavender else RunkkColors.Lime
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
            RunkkCheckRow(label = "전체 동의", isChecked = true, onCheckedChange = {}, isHighlighted = true)
            RunkkCheckRow(label = "서비스 이용약관", isChecked = true, onCheckedChange = {
            }, tag = "[필수]", isTagHighlighted = true, onView = {})
            RunkkSwitchRow(title = "음성 안내", description = "1km마다 알려 줘요", isChecked = true, onCheckedChange = {})
        }
    }
}

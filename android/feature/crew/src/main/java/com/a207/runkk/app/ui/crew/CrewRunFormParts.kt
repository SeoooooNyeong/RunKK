package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// 크루런 모집하기·수정 화면의 입력 부품(피그마 F-12 입력 상자, F-11c·F-12d·F-12e 휠, F-12f 라디오).

/** 칸 라벨(피그마 13 Medium textPrimary) + 9 간격 + 내용. */
@Composable
internal fun CrewFormField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium, lineHeight = 16),
            color = RunkkColors.TextPrimary
        )
        content()
    }
}

/**
 * 글자 입력 상자(피그마 Input): 바탕 surface, 테두리 1 divider, 모서리 12(내용 상자는 16), 안쪽 좌우 12·위아래 14, 최소 48, 15 Regular.
 * [minHeight]가 크면 여러 줄(내용 Text Area 72)이다.
 */
@Composable
internal fun CrewFormInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp
) {
    val isMultiline = minHeight > 48.dp
    val shape = RoundedCornerShape(if (isMultiline) 16.dp else 12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = !isMultiline,
        textStyle = RunkkTextStyles.text(15, lineHeight = 18).copy(color = RunkkColors.TextPrimary),
        cursorBrush = SolidColor(RunkkColors.TextPrimary),
        keyboardOptions = KeyboardOptions(imeAction = if (isMultiline) ImeAction.Default else ImeAction.Next),
        // 라벨은 칸 위 글자(CrewFormField)가 보인다. 입력값을 가리지 않게 contentDescription을 달지 않는다(자가 리뷰 M2).
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(shape)
                    .background(RunkkColors.Surface)
                    .border(1.dp, RunkkColors.Border, shape)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = if (isMultiline) Alignment.TopStart else Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = RunkkTextStyles.text(15, lineHeight = 18),
                        color = RunkkColors.TextSecondary
                    )
                }
                inner()
            }
        }
    )
}

/**
 * 고르는 칸(피그마 Input 모양, 누르면 시트): 값이 없으면 자리 문구(textSecondary). [isLocked]면 수정 화면의 잠긴 칸(바탕 surfaceHigh, 테두리
 * 없음, 글자 textSecondary)이라 누를 수 없다.
 */
@Composable
internal fun CrewFormSelect(
    value: String?,
    placeholder: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    val lockedText = stringResource(R.string.crew_run_write_locked)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (isLocked) RunkkColors.SurfaceHigh else RunkkColors.Surface)
            .then(if (isLocked) Modifier else Modifier.border(1.dp, RunkkColors.Border, shape))
            .clickable(enabled = !isLocked, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription =
                    listOfNotNull(value ?: placeholder, lockedText.takeIf { isLocked }).joinToString(", ")
                if (isLocked) disabled()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            value ?: placeholder,
            style = RunkkTextStyles.text(15, lineHeight = 18),
            color = if (value == null || isLocked) RunkkColors.TextSecondary else RunkkColors.TextPrimary
        )
    }
}

/**
 * 휠 피커 한 열(피그마 F-11c·F-12d·F-12e): 높이 180, 항목 36, 가운데 선택 띠(surfaceHigh, 모서리 12). 고른 값 18 Bold, 바로 옆 16 Regular
 * 0.45, 그 바깥 0.20. 굴려 멈춘 가운데 값이 [onSelect]로 가고, 항목을 누르면 그 값으로 굴러간다(보조 기술은 항목을 눌러 고른다).
 */
@Composable
internal fun CrewWheel(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spokenItems: List<String> = items
) {
    val list =
        rememberLazyListState(
            initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        )
    val scope = rememberCoroutineScope()
    // 보이는 항목 가운데 화면 가운데에 가장 가까운 항목이 지금 값이다.
    val center by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selectedIndex
        }
    }
    val latestSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .collect { latestSelect(center.coerceIn(0, items.lastIndex)) }
    }
    val selectedText = stringResource(R.string.crew_run_wheel_selected)
    Box(modifier = modifier.height(WHEEL_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM)
                .clip(RoundedCornerShape(12.dp))
                .background(RunkkColors.SurfaceHigh)
        )
        LazyColumn(
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = WHEEL_ITEM * 2),
            modifier = Modifier.fillMaxWidth().height(WHEEL_HEIGHT)
        ) {
            itemsIndexed(items) { index, item ->
                val distance = abs(index - center)
                val isCenter = distance == 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM)
                        .clickable(role = Role.Button) {
                            scope.launch { list.animateScrollToItem(index) }
                            latestSelect(index)
                        }
                        .semantics {
                            contentDescription = spokenItems.getOrElse(index) { item }
                            selected = index == selectedIndex
                            if (index == selectedIndex) stateDescription = selectedText
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item,
                        style = if (isCenter) {
                            RunkkTextStyles.SheetTitle
                        } else {
                            RunkkTextStyles.text(16, lineHeight = 19)
                        },
                        color = RunkkColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(
                            when (distance) {
                                0 -> 1f
                                1 -> NEAR_ALPHA
                                else -> FAR_ALPHA
                            }
                        )
                    )
                }
            }
        }
    }
}

private val WHEEL_ITEM = 36.dp
private val WHEEL_HEIGHT = 180.dp

private const val NEAR_ALPHA = 0.45f
private const val FAR_ALPHA = 0.20f

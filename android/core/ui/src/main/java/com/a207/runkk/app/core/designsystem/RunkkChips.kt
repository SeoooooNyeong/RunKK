package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 고르기 칩(피그마 RunKK / Chip / 시간대 칩, S15P31A207-292): 높이 32, 알약, 좌우 12, 글자 13. 안 고른 칩은 divider 테두리에
 * 본문색 Regular, 고른 칩은 [RunkkColors.ChipSelected](#67705B) 채움에 굵은 글자이고 체크를 붙이지 않는다. 누르는 칸은 48dp다.
 * 보조 기술에는 켜짐/꺼짐 토글로 보인다.
 */
@Composable
fun RunkkChoiceChip(
    label: String,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RunkkShapes.Pill
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = isSelected,
                interactionSource = interaction,
                indication = null,
                enabled = isEnabled,
                role = Role.Checkbox,
                onValueChange = onSelectedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .testTag(CHOICE_CHIP_BODY_TAG)
                .heightIn(min = CHIP_HEIGHT)
                .runkkPressScale(interaction)
                .clip(shape)
                .background(if (isSelected) RunkkColors.ChipSelected else Color.Transparent)
                .then(if (isSelected) Modifier else Modifier.border(1.dp, RunkkColors.Border, shape))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = RunkkTextStyles.text(13, if (isSelected) FontWeight.Bold else FontWeight.Normal),
                color = if (isSelected) RunkkColors.OnChipSelected else RunkkColors.TextPrimary
            )
        }
    }
}

/** 고르기 칩 몸통(보이는 32 알약)의 시험 표지. */
internal const val CHOICE_CHIP_BODY_TAG = "runkk-choice-chip-body"

private val CHIP_HEIGHT: Dp = 32.dp

/**
 * 하나만 고르는 선택지 한 칸(피그마 M3 Segmented button 한 칸): 보이는 높이 40, 글자 14 Medium. 고른 칸은 [RunkkColors.SelectedFill]
 * (라임)로 칠하고 글자 앞에 체크(18, 간격 8)를 붙인다. 안 고른 칸은 표면에 divider 테두리다. 칸이 좁은 줄(테마, 거리 단위)은
 * [isCheckShown]을 꺼 체크를 뺀다. [shape]로 붙은 줄의 양 끝만 둥글게 한다. 여러 개를 [RunkkSegmentRow]로 묶으면 라디오 묶음이 된다.
 */
@Composable
fun RunkkSegmentOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    isCheckShown: Boolean = true,
    shape: Shape = RunkkShapes.Pill
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .clip(shape)
                .background(if (isSelected) RunkkColors.SelectedFill else RunkkColors.Surface)
                .border(1.dp, RunkkColors.Border, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected && isCheckShown) ChipCheck(RunkkColors.OnSelectedFill)
                Text(
                    text = label,
                    style = RunkkTextStyles.text(14, FontWeight.Medium, lineHeight = 20),
                    color = if (isSelected) RunkkColors.OnSelectedFill else RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** 고른 선택지 글자 앞 체크(18, 글자와 8 띄움, 피그마 Segmented button Selected icon). 읽는 글자가 아니다. */
@Composable
private fun ChipCheck(tint: Color) {
    Icon(
        RunkkIcons.Check,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.padding(end = CHECK_GAP).size(CHECK_SIZE).testTag(CHIP_CHECK_TAG)
    )
}

/** 고른 선택지 체크의 시험 표지. */
internal const val CHIP_CHECK_TAG = "runkk-chip-check"

private val CHECK_SIZE: Dp = 18.dp
private val CHECK_GAP: Dp = 8.dp

/**
 * [RunkkSegmentOption]을 같은 너비로 붙여 놓은 라디오 묶음(피그마 M3 Segmented button: 칸이 붙고 양 끝만 알약, 테두리 divider).
 * [groupLabel]이 있으면 위에 묶음 이름(12, 보조색)을 보이고, [spokenLabels]가 있으면 선택지마다 보조 기술이 그 문구로 읽는다
 * ("4회" → "주 4회").
 */
@Composable
fun RunkkSegmentRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String? = null,
    spokenLabels: List<String>? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 묶음 이름은 선택 묶음 노드가 읽는다(보이는 글자는 두 번 읽지 않게 숨긴다).
        groupLabel?.let {
            Text(
                text = it,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
        Row(
            modifier = Modifier
                .selectableGroup()
                .then(
                    if (groupLabel != null) {
                        Modifier.semantics {
                            contentDescription = groupLabel
                            isTraversalGroup = true
                        }
                    } else {
                        Modifier
                    }
                ),
            // 이웃 칸 테두리가 겹쳐 한 줄로 보이게(피그마 간격 -1).
            horizontalArrangement = Arrangement.spacedBy((-1).dp)
        ) {
            options.forEachIndexed { index, label ->
                val spoken = spokenLabels?.getOrNull(index)
                RunkkSegmentOption(
                    label = label,
                    isSelected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    // 칸이 5개 이상이면 칸이 좁아 체크를 넣으면 글자("7회")가 잘린다
                    isCheckShown = options.size <= MAX_OPTIONS_WITH_CHECK,
                    shape = segmentShape(index, options.size),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
                )
            }
        }
    }
}

/** 붙은 줄의 칸 모양: 첫 칸은 왼쪽만, 끝 칸은 오른쪽만 알약, 가운데는 네모. */
private fun segmentShape(index: Int, count: Int): Shape = when {
    count == 1 -> RunkkShapes.Pill
    index == 0 -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    index == count - 1 -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    else -> RectangleShape
}

/**
 * 정보 칩(높이 32, 모서리 16): "지난 설정 그대로" 요약처럼 누르지 않는 표시.
 * [isHighlighted]면 라임 테두리·글자, [isDisplay]면 숫자 글꼴("5km"). [spoken]이 있으면 보조 기술이 그 문구로 읽고, 없으면 "5km"처럼
 * 한글로 읽어야 하는 라벨만 바꿔 읽는다.
 */
@Composable
fun RunkkInfoChip(
    label: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    isDisplay: Boolean = false,
    spoken: String? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(if (isHighlighted) RunkkColors.LimeContainer else Color.Transparent)
            .border(1.dp, if (isHighlighted) RunkkColors.Lime else RunkkColors.BorderStrong, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            modifier = if (spoken !=
                null
            ) {
                Modifier.semantics { contentDescription = spoken }
            } else {
                Modifier.spokenIfDifferent(label)
            },
            style = when {
                isDisplay -> RunkkTextStyles.display(16)
                isHighlighted -> RunkkTextStyles.text(12, FontWeight.Bold)
                else -> RunkkTextStyles.Caption
            },
            color = if (isHighlighted) RunkkColors.Lime else RunkkColors.TextSecondary
        )
    }
}

/**
 * 상태 표시(높이 32, 모서리 16, 표면): "GPS 좋음", "화면 잠김". 앞에 점이나 아이콘을 둘 수 있다. [live]가 있으면 문구가 바뀔 때
 * 보조 기술에 알린다(알림 영역은 문구 노드에 건다).
 */
@Composable
fun RunkkStatusPill(
    label: String,
    modifier: Modifier = Modifier,
    live: LiveRegionMode? = null,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Surface)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier
                .spokenIfDifferent(label)
                .then(if (live != null) Modifier.semantics { liveRegion = live } else Modifier)
        )
    }
}

/** 라벨을 한글로 읽어야 하면("5km" → "5킬로미터") 읽기 문구를 단다. */
@Composable
private fun Modifier.spokenIfDifferent(label: String): Modifier {
    val spoken = RunkkFormat.spokenLabel(label)
    return if (spoken == label) this else semantics { contentDescription = spoken }
}

/** 작은 꼬리표(높이 28, 모서리 8): "자유런", "유효 러닝"(라임), "전체공개". */
enum class RunkkBadgeTone { Neutral, Lime, Muted }

@Composable
fun RunkkBadge(label: String, modifier: Modifier = Modifier, tone: RunkkBadgeTone = RunkkBadgeTone.Neutral) {
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (tone == RunkkBadgeTone.Lime) RunkkColors.LimeContainer else RunkkColors.Surface)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = RunkkTextStyles.text(12, if (tone == RunkkBadgeTone.Lime) FontWeight.Bold else FontWeight.Normal),
            color = when (tone) {
                RunkkBadgeTone.Neutral -> RunkkColors.TextPrimary
                RunkkBadgeTone.Lime -> RunkkColors.Lime
                RunkkBadgeTone.Muted -> RunkkColors.TextSecondary
            }
        )
    }
}

/**
 * 세그먼트(피그마 RunKK / Control / 세그먼트, S15P31A207-292): surfaceHigh 트랙(높이 44, 모서리 22, 안쪽 4)에 같은 너비 칸(높이 36,
 * 모서리 18)을 놓는다. 고른 칸은 [selection]이 [RunkkSegmentedSelection.White]면 흰색(color/segmentSelected, 2탭 폼),
 * [RunkkSegmentedSelection.Lime]이면 라임(크루 홈 3탭)이고 글자는 14 Bold, 안 고른 칸은 14 Regular 본문색이다.
 * 누르는 칸 높이는 48dp다. 보조 기술에는 탭으로 보인다.
 *
 * ```
 * RunkkSegmented(listOf("공개", "비공개"), selectedIndex = 0, onSelect = onVisibility)
 * RunkkSegmented(listOf("레이스", "크루런", "채팅"), selected, onSelect, selection = RunkkSegmentedSelection.Lime)
 * ```
 */
@Composable
fun RunkkSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selection: RunkkSegmentedSelection = RunkkSegmentedSelection.White
) {
    Box(modifier = modifier.heightIn(min = SEGMENT_TOUCH_HEIGHT), contentAlignment = Alignment.Center) {
        // 트랙(44)은 48 누름 줄 뒤에 위아래 2씩 들여 그린다. 칸은 48 높이로 눌리고 36 알약만 보인다.
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = (SEGMENT_TOUCH_HEIGHT - SEGMENT_TRACK_HEIGHT) / 2)
                .clip(RunkkShapes.Pill)
                .background(RunkkColors.SurfaceHigh)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SEGMENT_TRACK_PADDING)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val fill = when {
                    !isSelected -> Color.Transparent
                    selection == RunkkSegmentedSelection.White -> RunkkColors.SegmentSelected
                    else -> RunkkColors.TabSelected
                }
                val content = if (isSelected && selection == RunkkSegmentedSelection.Lime) {
                    RunkkColors.OnTabSelected
                } else {
                    RunkkColors.TextPrimary
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SEGMENT_TOUCH_HEIGHT)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center
                ) {
                    SegmentPill(label, fill, content, isSelected)
                }
            }
        }
    }
}

/** 세그먼트 한 칸의 보이는 알약(높이 36, 모서리 18). */
@Composable
private fun SegmentPill(label: String, fill: Color, content: Color, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SEGMENT_HEIGHT)
            .clip(RunkkShapes.Pill)
            .background(fill)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = RunkkTextStyles.text(
                14,
                if (isSelected) FontWeight.Bold else FontWeight.Normal,
                lineHeight = 17
            ),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** [RunkkSegmented] 고른 칸 색. */
enum class RunkkSegmentedSelection { White, Lime }

private val SEGMENT_TRACK_HEIGHT: Dp = 44.dp
private val SEGMENT_TOUCH_HEIGHT: Dp = 48.dp
private val SEGMENT_TRACK_PADDING: Dp = 4.dp
private val SEGMENT_HEIGHT: Dp = 36.dp

/**
 * 탭 전환(자유런/배틀런, 달력/목록). 피그마 세그먼트([RunkkSegmented], 고른 칸 라임) 모양이다. [isCompact]면 상단 바 안에 넣는
 * 작은 줄(칸 높이 32, 글자 13)이고 칸 너비가 글자에 맞는다. 보조 기술에는 탭으로 보인다.
 */
@Composable
fun RunkkSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    if (!isCompact) {
        RunkkSegmented(options, selectedIndex, onSelect, modifier, selection = RunkkSegmentedSelection.Lime)
        return
    }
    Row(
        modifier = modifier.clip(RunkkShapes.Pill).background(RunkkColors.SurfaceHigh).padding(4.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clip(RunkkShapes.Pill)
                        .background(if (isSelected) RunkkColors.TabSelected else Color.Transparent)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = RunkkTextStyles.text(13, if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        color = if (isSelected) RunkkColors.OnTabSelected else RunkkColors.TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * 같은 너비 탭 줄(탭이 많을 때, 시안 HiMyCrew). 피그마 세그먼트([RunkkSegmented], 고른 칸 라임)와 같다. 보조 기술에는 탭으로 보인다.
 */
@Composable
fun RunkkTabRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    RunkkSegmented(options, selectedIndex, onSelect, modifier, selection = RunkkSegmentedSelection.Lime)
}

/** 분류 제목 + 고르기 칩 줄(가입 성향 태그, 크루 태그). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunkkChipGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = RunkkTextStyles.text(12, FontWeight.Bold), color = RunkkColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            content()
        }
    }
}

@Preview(name = "칩", backgroundColor = 0xFF09090B, showBackground = true, widthDp = 390)
@Composable
private fun RunkkChipsPreview() {
    RunkkTheme(reduceMotion = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RunkkChipGroup("동행") {
                RunkkChoiceChip("혼자가 편해요", isSelected = false, onSelectedChange = {})
                RunkkChoiceChip("함께가 좋아요", isSelected = true, onSelectedChange = {})
            }
            RunkkSegmentRow(listOf("전체공개", "크루공개", "비공개"), selectedIndex = 0, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkInfoChip("5km", isHighlighted = true, isDisplay = true)
                RunkkInfoChip("음성 켬")
                RunkkStatusPill("GPS 좋음")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkBadge("자유런")
                RunkkBadge("유효 러닝", tone = RunkkBadgeTone.Lime)
                RunkkBadge("전체공개", tone = RunkkBadgeTone.Muted)
            }
            RunkkSegmentedTabs(listOf("자유런", "배틀런"), selectedIndex = 0, onSelect = {
            }, modifier = Modifier.fillMaxWidth())
            RunkkSegmentedTabs(listOf("달력", "목록"), selectedIndex = 1, onSelect = {}, isCompact = true)
        }
    }
}

/** 체크를 붙일 수 있는 한 줄 선택지 수의 상한. 넘으면 칸이 좁아 체크를 뺀다. */
private const val MAX_OPTIONS_WITH_CHECK = 4

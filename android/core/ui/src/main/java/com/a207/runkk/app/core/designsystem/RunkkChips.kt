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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 고르기 칩(높이 40, 모서리 20): 성향 태그처럼 여러 개를 켜고 끄는 선택. 켜면 라임으로 채운다.
 * 보조 기술에는 켜짐/꺼짐 토글로 보인다.
 */
@Composable
fun RunkkChoiceChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSelectedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .runkkPressScale(interaction)
                .clip(shape)
                .background(if (selected) RunkkColors.Lime else RunkkColors.Surface)
                .border(1.dp, if (selected) RunkkColors.Lime else RunkkColors.BorderStrong, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = RunkkTextStyles.text(13, if (selected) FontWeight.Black else FontWeight.Normal),
                color = if (selected) RunkkColors.OnLime else RunkkColors.TextSecondary
            )
        }
    }
}

/**
 * 하나만 고르는 선택지(높이 40, 모서리 12): 공개 범위처럼 고른 것은 라임 테두리 + 어두운 라임 배경.
 * 여러 개를 [RunkkSegmentRow]로 묶으면 라디오 묶음이 된다.
 */
@Composable
fun RunkkSegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RunkkShapes.Small
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
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
                .height(height)
                .runkkPressScale(interaction)
                .clip(shape)
                .background(if (selected) RunkkColors.LimeContainer else Color.Transparent)
                .border(1.dp, if (selected) RunkkColors.Lime else RunkkColors.BorderStrong, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = RunkkTextStyles.text(13, if (selected) FontWeight.Bold else FontWeight.Normal),
                color = if (selected) RunkkColors.Lime else RunkkColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** [RunkkSegmentOption]을 같은 너비로 나란히 놓은 라디오 묶음. */
@Composable
fun RunkkSegmentRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            RunkkSegmentOption(label = label, selected = index == selectedIndex, onClick = {
                onSelect(index)
            }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 정보 칩(높이 32, 모서리 16): "지난 설정 그대로" 요약처럼 누르지 않는 표시.
 * [highlighted]면 라임 테두리·글자, [display]면 숫자 글꼴("5K").
 */
@Composable
fun RunkkInfoChip(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    display: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(if (highlighted) RunkkColors.LimeContainer else Color.Transparent)
            .border(1.dp, if (highlighted) RunkkColors.Lime else RunkkColors.BorderStrong, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = when {
                display -> RunkkTextStyles.display(16)
                highlighted -> RunkkTextStyles.text(12, FontWeight.Bold)
                else -> RunkkTextStyles.Caption
            },
            color = if (highlighted) RunkkColors.Lime else RunkkColors.TextSecondary
        )
    }
}

/** 상태 표시(높이 32, 모서리 16, 표면): "GPS 좋음", "화면 잠김". 앞에 점이나 아이콘을 둘 수 있다. */
@Composable
fun RunkkStatusPill(label: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Surface)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(text = label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** 작은 꼬리표(높이 28, 모서리 8): "자유런", "유효 러닝"(라임), "전체공개". */
enum class RunkkBadgeTone { Neutral, Lime, Muted }

@Composable
fun RunkkBadge(label: String, modifier: Modifier = Modifier, tone: RunkkBadgeTone = RunkkBadgeTone.Neutral) {
    Box(
        modifier = modifier
            .height(28.dp)
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
 * 탭 전환(자유런/배틀런, 달력/목록). [compact]면 높이 32(상단 바 안), 아니면 40.
 * 보조 기술에는 탭으로 보인다.
 */
@Composable
fun RunkkSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val container = if (compact) RunkkColors.Surface else RunkkColors.Background
    val outer = if (compact) RunkkShapes.Small else RunkkShapes.Medium
    val inner = if (compact) RoundedCornerShape(8.dp) else RunkkShapes.Small
    Row(
        modifier = modifier.clip(outer).background(container).padding(4.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val itemModifier = if (compact) Modifier else Modifier.weight(1f)
            Box(
                modifier = itemModifier
                    .height(if (compact) 40.dp else 48.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .then(if (compact) Modifier else Modifier.fillMaxWidth())
                        .height(if (compact) 32.dp else 40.dp)
                        .clip(inner)
                        .background(if (selected) RunkkColors.Border else Color.Transparent)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = if (compact) {
                            RunkkTextStyles.text(13, if (selected) FontWeight.Bold else FontWeight.Normal)
                        } else {
                            RunkkTextStyles.text(15, if (selected) FontWeight.Black else FontWeight.Normal)
                        },
                        color = if (selected) RunkkColors.TextPrimary else RunkkColors.TextSecondary
                    )
                }
            }
        }
    }
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
                RunkkChoiceChip("혼자가 편해요", selected = false, onSelectedChange = {})
                RunkkChoiceChip("함께가 좋아요", selected = true, onSelectedChange = {})
            }
            RunkkSegmentRow(listOf("전체공개", "크루공개", "비공개"), selectedIndex = 0, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkInfoChip("5K", highlighted = true, display = true)
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
            RunkkSegmentedTabs(listOf("달력", "목록"), selectedIndex = 1, onSelect = {}, compact = true)
        }
    }
}

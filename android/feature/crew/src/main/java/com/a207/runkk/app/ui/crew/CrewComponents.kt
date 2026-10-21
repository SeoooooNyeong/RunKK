package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkChipGroup
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.runkkPressScale
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy

/**
 * 라임 알약 버튼(높이 40, 시안 크루 "+ 만들기"·"가입"). 디자인 시스템에 라임 알약이 없어 크루 모듈 안에 둔다(올릴 후보).
 */
@Composable
internal fun CrewLimePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .runkkPressScale(interaction)
                .clip(RoundedCornerShape(20.dp))
                .background(RunkkColors.Lime)
                .alpha(if (enabled) 1f else DISABLED)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = RunkkTextStyles.text(13, FontWeight.Black), color = RunkkColors.OnLime)
        }
    }
}

private const val DISABLED = 0.4f

/** 크루 로고 자리(이니셜). 로고 이미지는 크루 정보 응답에 아직 없다. */
@Composable
internal fun CrewLogo(name: String, size: Int = 48, accent: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(if (accent) RunkkColors.LimeContainer else RunkkColors.Border)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).ifEmpty { "C" },
            style = RunkkTextStyles.text(size / 2 - 4, FontWeight.Black),
            color = if (accent) RunkkColors.Lime else RunkkColors.TextSecondary
        )
    }
}

/** 크루 만들기·정보 수정 공통 입력(FR-110·FR-113): 이름·소개·태그(7분류, 분류마다 1개, 1~5개)·공개 범위·가입 방식. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewFormFields(form: CrewFormState, onForm: (CrewFormState) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RunkkTextField(
            value = form.name,
            onValueChange = { onForm(form.copy(name = it)) },
            label = "크루명 · 2~20자",
            placeholder = "크루명",
            maxLength = CrewFormState.NAME_MAX
        )
        RunkkTextField(
            value = form.intro,
            onValueChange = { onForm(form.copy(intro = it)) },
            label = "한 줄 소개 · 40자",
            placeholder = "우리 크루를 한 줄로",
            maxLength = CrewFormState.INTRO_MAX
        )
        Text(
            "성향 태그 · 분류마다 1개, 모두 1~5개 (${form.tags.size}/${RunkkTags.MAX_SELECTED})",
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        CREW_TAG_CATEGORIES.forEach { category ->
            RunkkChipGroup(category.name) {
                category.tags.forEach { tag ->
                    RunkkChoiceChip(
                        label = tag.label,
                        selected = tag.id in form.tags,
                        onSelectedChange = { onForm(form.copy(tags = RunkkTags.toggle(form.tags, tag.id))) }
                    )
                }
            }
        }
        Text("공개 범위", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        RunkkSegmentRow(
            options = listOf("공개", "비공개(초대 코드로만)"),
            selectedIndex = form.visibility.ordinal,
            onSelect = { onForm(form.copy(visibility = CrewVisibility.entries[it])) }
        )
        Text("가입 방식", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        RunkkSegmentRow(
            options = JoinPolicy.entries.map { it.label },
            selectedIndex = form.joinPolicy.ordinal,
            onSelect = { onForm(form.copy(joinPolicy = JoinPolicy.entries[it])) }
        )
    }
}

/**
 * 같은 너비 탭 줄(시안 HiMyCrew: 높이 40, 글자 13, 고른 탭은 라임). RunkkSegmentedTabs는 15sp라 네 칸에서 "응원보드"가 줄바꿈되어
 * 크루 모듈 안에 둔다(디자인 시스템으로 올릴 후보: 균등·13sp 탭). 보조 기술에는 탭으로 보인다.
 */
@Composable
internal fun CrewTabRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Surface)
            .padding(4.dp)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) RunkkColors.Lime else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = RunkkTextStyles.text(13, if (selected) FontWeight.Black else FontWeight.Normal),
                        color = if (selected) RunkkColors.OnLime else RunkkColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 작은 줄 하나: 라벨 + 값(크루 상세·관리). */
@Composable
internal fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Text(value, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
    }
}

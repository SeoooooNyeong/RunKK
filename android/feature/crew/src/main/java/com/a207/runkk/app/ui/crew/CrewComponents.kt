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
import androidx.compose.ui.res.stringResource
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
            label = stringResource(R.string.crew_form_name_label),
            placeholder = stringResource(R.string.crew_form_name_placeholder),
            maxLength = CrewFormState.NAME_MAX
        )
        RunkkTextField(
            value = form.intro,
            onValueChange = { onForm(form.copy(intro = it)) },
            label = stringResource(R.string.crew_form_intro_label),
            placeholder = stringResource(R.string.crew_form_intro_placeholder),
            maxLength = CrewFormState.INTRO_MAX
        )
        Text(
            stringResource(R.string.crew_form_tags_caption, form.tags.size, RunkkTags.MAX_SELECTED),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        CrewTagChoices(form, onForm)
        RunkkSegmentRow(
            options = listOf(
                stringResource(R.string.crew_form_visibility_public),
                stringResource(R.string.crew_form_visibility_private)
            ),
            selectedIndex = form.visibility.ordinal,
            onSelect = { onForm(form.copy(visibility = CrewVisibility.entries[it])) },
            groupLabel = stringResource(R.string.crew_form_visibility_group)
        )
        RunkkSegmentRow(
            options = JoinPolicy.entries.map { stringResource(it.labelRes) },
            selectedIndex = form.joinPolicy.ordinal,
            onSelect = { onForm(form.copy(joinPolicy = JoinPolicy.entries[it])) },
            groupLabel = stringResource(R.string.crew_form_join_policy_group)
        )
    }
}

/** 성향 태그 분류별 칩. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrewTagChoices(form: CrewFormState, onForm: (CrewFormState) -> Unit) {
    CREW_TAG_CATEGORIES.forEach { category ->
        RunkkChipGroup(stringResource(category.nameRes)) {
            category.tags.forEach { tag ->
                RunkkChoiceChip(
                    label = stringResource(tag.labelRes),
                    selected = tag.id in form.tags,
                    onSelectedChange = { onForm(form.copy(tags = RunkkTags.toggle(form.tags, tag.id))) }
                )
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

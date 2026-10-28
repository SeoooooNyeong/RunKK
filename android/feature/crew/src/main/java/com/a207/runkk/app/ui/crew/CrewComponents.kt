package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkChipGroup
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy

/** 크루 로고 자리(이니셜). 로고 이미지는 크루 정보 응답에 아직 없다. */
@Composable
internal fun CrewLogo(name: String, size: Int = 48, isAccent: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(
                if (isAccent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant
            )
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).ifEmpty { "C" },
            style = RunkkTextStyles.text(size / 2 - 4, FontWeight.Black),
            color = if (isAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
        // 와이어 I2: 분류 제목 없이 한 묶음으로 보인다. 같은 분류에서 하나만 고르는 규칙은 RunkkTags.toggle이 지킨다.
        RunkkChipGroup(stringResource(R.string.crew_form_tags_caption, RunkkTags.MAX_SELECTED)) {
            CREW_TAG_CATEGORIES.flatMap { it.tags }.forEach { tag ->
                RunkkChoiceChip(
                    label = stringResource(tag.labelRes),
                    isSelected = tag.id in form.tags,
                    onSelectedChange = { onForm(form.copy(tags = RunkkTags.toggle(form.tags, tag.id))) }
                )
            }
        }
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
            options = FORM_JOIN_POLICIES.map { stringResource(it.longLabelRes) },
            selectedIndex = FORM_JOIN_POLICIES.indexOf(form.joinPolicy),
            onSelect = { onForm(form.copy(joinPolicy = FORM_JOIN_POLICIES[it])) },
            groupLabel = stringResource(R.string.crew_form_join_policy_group)
        )
    }
}

/** 가입 방식 선택지 순서(와이어 I2): 바로 가입, 승인 후 가입. */
private val FORM_JOIN_POLICIES: List<JoinPolicy> = listOf(JoinPolicy.FREE, JoinPolicy.APPROVAL)

/** 작은 줄 하나: 라벨 + 값(크루 상세·관리). */
@Composable
internal fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = RunkkTextStyles.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = RunkkTextStyles.Caption, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * 크루 화면 카드: RunkkCard에 MaterialTheme 표면·테두리 색을 넣는다(라이트·다크 테마, NFR-041). [isHighlighted]면 라임 테두리.
 */
@Composable
internal fun CrewCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    RunkkCard(
        modifier = modifier,
        borderColor = if (isHighlighted) colors.primary else colors.outlineVariant,
        background = colors.surface,
        content = content
    )
}

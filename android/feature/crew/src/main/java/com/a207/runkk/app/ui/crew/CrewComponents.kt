package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Distance
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

/** 크루 만들기·정보 수정 공통 입력(FR-110·FR-113, 피그마 F-04·F-26): 이름·소개·태그(평일·주말 시간대, 1~3개)·공개 범위·가입 방식. */
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
        CrewTagPicker(
            selected = form.tags,
            onToggle = { onForm(form.copy(tags = RunkkTags.toggle(form.tags, it))) },
            title = stringResource(R.string.crew_form_tags_caption, RunkkTags.MAX_SELECTED)
        )
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

/** 크루 통계 칸(피그마 F-02·F-18): 작은 이름과 큰 값. 값을 아직 모르면([value]가 null) 빈 값 표시. */
@Composable
internal fun CrewStatCard(label: String, value: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    CrewCard(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = RunkkTextStyles.Caption, color = colors.onSurfaceVariant)
        Text(
            value ?: RunkkFormat.EMPTY,
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = colors.onSurface
        )
    }
}

/** "64.27km"(소수 둘째 자리). */
@Composable
internal fun kmLabel(distance: Distance): String =
    stringResource(R.string.crew_contribution_km, RunkkFormat.km(distance))

/**
 * 크루 태그 고르기(피그마 F-01b·F-04·F-26): 줄마다 "평일"·"주말"과 시간대 칩 넷. 3개를 넘기면 [RunkkTags.toggle]이 더 켜지 않는다.
 * [title]이 있으면 위에 둔다(만들기·수정의 "태그 최대 3개").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewTagPicker(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(it, style = RunkkTextStyles.text(12, FontWeight.Bold), color = colors.onSurfaceVariant)
        }
        CREW_TAG_CATEGORIES.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(category.nameRes),
                    style = RunkkTextStyles.BodyStrong,
                    color = colors.onSurface,
                    modifier = Modifier.width(TAG_ROW_LABEL_WIDTH)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    category.tags.forEach { tag ->
                        RunkkChoiceChip(
                            label = stringResource(tag.slotRes),
                            isSelected = tag.id in selected,
                            onSelectedChange = { onToggle(tag.id) }
                        )
                    }
                }
            }
        }
    }
}

/** 크루 태그 배지 줄(피그마 F-01c·F-02·F-18): "평일 저녁"처럼 전체 이름, 주말은 라임 배지. 목록 순서로 늘어놓는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewTagBadges(tags: Collection<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkTags.sorted(tags).forEach { id ->
            RunkkBadge(
                RunkkTags.label(id),
                tone = if (RunkkTags.of(id)?.isWeekend == true) RunkkBadgeTone.Lime else RunkkBadgeTone.Neutral
            )
        }
    }
}

/** 태그 줄 앞 "평일"·"주말" 칸 너비. */
private val TAG_ROW_LABEL_WIDTH = 44.dp

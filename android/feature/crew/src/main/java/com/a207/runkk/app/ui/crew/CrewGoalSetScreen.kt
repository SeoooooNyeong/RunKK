package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CrewGoalSetRoute(key: Routes.CrewGoalSet, viewModel: CrewGoalSetViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key.crewId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(state.isSaved) { if (state.isSaved) navigator.back() }
    CrewGoalSetScreen(
        state,
        GoalSetActions(
            onBack = navigator::back,
            onDraft = viewModel::onDraft,
            onSave = viewModel::onSave
        )
    )
}

/**
 * 주간 목표(피그마 F-07): 「10월 4주차」 + 이번 주 날짜, 목표 거리 알약 입력(값 Bold 20 + km), 지난 목표 3주 카드(달성·달성 실패, 100을 넘는
 * 진행률 그대로, 막대는 100%에서 꽉 참, 참여 인원), 저장.
 */
@Composable
fun CrewGoalSetScreen(state: CrewGoalSetUiState, actions: GoalSetActions, modifier: Modifier = Modifier) {
    WriteScaffold(
        state = WriteScaffoldState(
            title = stringResource(R.string.crew_goal_set_title),
            submitLabel = stringResource(R.string.crew_goal_set_save),
            isSubmitEnabled = state.canSave,
            isSaving = state.isSaving,
            error = state.error
        ),
        onBack = actions.onBack,
        onSubmit = actions.onSave,
        modifier = modifier
    ) {
        if (state.weekStart != LocalDate.MIN) {
            val (month, week) = weekOfMonth(state.weekStart)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.crew_goal_set_week_title, month, week),
                    style = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 19),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(
                        R.string.crew_goal_set_week,
                        weekDay(state.weekStart),
                        weekDay(state.weekStart.plusDays(WEEK_LAST_DAY))
                    ),
                    style = RunkkTextStyles.text(13, lineHeight = 16),
                    color = RunkkColors.TextPrimary
                )
            }
        }
        GoalInput(state.draft, actions.onDraft)
        if (state.pastWeeks.isNotEmpty()) {
            Text(
                stringResource(R.string.crew_goal_set_past),
                style = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 19),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.pastWeeks.forEach { PastGoalCard(it) }
            }
        }
    }
}

/** 목표 거리 입력(피그마 F-07): 라벨 Medium 13, 52 높이 알약(surface + 테두리, 좌우 12), 값 Bold 20 + 8 + 「km」 Bold 18. */
@Composable
private fun GoalInput(draft: String, onDraft: (String) -> Unit) {
    val label = stringResource(R.string.crew_goal_set_label)
    val shape = RoundedCornerShape(26.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = RunkkTextStyles.text(13, FontWeight.Medium, lineHeight = 16),
            color = RunkkColors.TextPrimary
        )
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            singleLine = true,
            textStyle = RunkkTextStyles.text(
                20,
                FontWeight.Bold,
                lineHeight = 24
            ).copy(color = RunkkColors.TextPrimary),
            cursorBrush = SolidColor(RunkkColors.TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(shape)
                        .background(RunkkColors.Surface)
                        .border(1.dp, RunkkColors.Border, shape)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    Text(
                        stringResource(R.string.crew_goal_set_unit),
                        style = RunkkTextStyles.text(18, FontWeight.Bold, lineHeight = 22),
                        color = RunkkColors.TextPrimary
                    )
                }
            }
        )
    }
}

/**
 * 지난 목표 한 주(피그마 F-07): 투명 바탕 + 테두리 1, r28, 안쪽 14, 간격 8. 「10월 3주차」와 오른쪽 달성·달성 실패·목표 없음, 합산 Bold 22 +
 * 「/ 80 km」 + 진행률 Bold 15(100을 넘으면 그대로), 8 두께 막대(barCrew, 100%에서 꽉 참), 참여 인원.
 */
@Composable
private fun PastGoalCard(race: RaceStatus) {
    val (month, week) = weekOfMonth(race)
    val weekLabel = stringResource(R.string.crew_goal_set_week_title, month, week)
    val goal = race.goal
    val progress = goalProgressLabel(race)
    val status = stringResource(
        when {
            goal == null -> R.string.crew_goal_set_no_goal
            race.achievedAt != null -> R.string.crew_goal_set_achieved
            else -> R.string.crew_goal_set_missed
        }
    )
    val total = RunkkFormat.kmShort(race.total)
    val description = stringResource(R.string.crew_goal_set_past_description, weekLabel, total, status)
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(14.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                weekLabel,
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                status,
                style = RunkkTextStyles.text(12, FontWeight.Bold, lineHeight = 18),
                color = RunkkColors.TextPrimary
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                total,
                style = RunkkTextStyles.text(22, FontWeight.Bold, lineHeight = 26),
                color = RunkkColors.TextPrimary
            )
            Text(
                if (goal != null) {
                    stringResource(R.string.crew_goal_of, RunkkFormat.kmShort(goal))
                } else {
                    stringResource(R.string.crew_goal_km_unit)
                },
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).padding(bottom = 3.dp)
            )
            if (progress != null) {
                Text(
                    stringResource(R.string.crew_race_share_percent, progress),
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary
                )
            }
        }
        if (goal != null) RaceBar(total = goalFillFraction(race))
        Text(
            stringResource(R.string.crew_goal_set_participants, participantCount(race)),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextPrimary
        )
    }
}

/** "10.20 (월)". 틀은 문자열 리소스(crew_goal_set_day_pattern)다. */
@Composable
private fun weekDay(day: LocalDate): String = day.format(
    DateTimeFormatter.ofPattern(
        LocalContext.current.resources.getString(R.string.crew_goal_set_day_pattern),
        Locale.KOREAN
    )
)

/** 월요일에서 일요일까지 더할 날 수. */
private const val WEEK_LAST_DAY = 6L

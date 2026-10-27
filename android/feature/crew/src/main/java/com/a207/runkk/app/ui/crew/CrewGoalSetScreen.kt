package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkTextField
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
            onQuick = viewModel::onQuick,
            onSave = viewModel::onSave
        )
    )
}

/** 주간 공동 목표(와이어프레임 M8): 이번 주 날짜, 목표 거리(km), 빠른 선택 50·80·100km, 지난 주 결과 카드, 저장. */
@Composable
fun CrewGoalSetScreen(state: CrewGoalSetUiState, actions: GoalSetActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
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
            Text(
                stringResource(
                    R.string.crew_goal_set_week,
                    weekDay(state.weekStart),
                    weekDay(state.weekStart.plusDays(WEEK_LAST_DAY))
                ),
                style = RunkkTextStyles.Body,
                color = colors.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkTextField(
                value = state.draft,
                onValueChange = actions.onDraft,
                label = stringResource(R.string.crew_goal_set_label),
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.crew_goal_set_unit),
                style = RunkkTextStyles.BodyStrong,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        val quick = stringResource(R.string.crew_goal_set_quick)
        Row(
            Modifier.semantics {
                selectableGroup()
                contentDescription = quick
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CrewGoalSetUiState.QUICK_KM.forEach { km ->
                BoardChip(
                    stringResource(R.string.crew_goal_set_quick_km, km),
                    isSelected = state.draftKm == km.toDouble(),
                    onClick = { actions.onQuick(km) }
                )
            }
        }
        state.lastWeek?.let { LastWeekCard(it) }
    }
}

/** 지난 주 결과: 달성 여부, 합산/목표 거리, 막대, 참여 인원. */
@Composable
private fun LastWeekCard(last: RaceStatus) {
    val colors = MaterialTheme.colorScheme
    CrewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.crew_goal_set_last_week),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(
                    when {
                        last.goal == null -> R.string.crew_goal_set_no_goal
                        last.achievedAt != null -> R.string.crew_goal_set_achieved
                        else -> R.string.crew_goal_set_missed
                    }
                ),
                style = RunkkTextStyles.Caption,
                color = if (last.achievedAt != null) colors.primary else colors.onSurfaceVariant
            )
        }
        val goal = last.goal
        Text(
            if (goal != null) {
                stringResource(
                    R.string.crew_goal_set_last_distance,
                    RunkkFormat.kmShort(last.total),
                    RunkkFormat.kmShort(goal)
                )
            } else {
                stringResource(R.string.crew_goal_set_last_total, RunkkFormat.kmShort(last.total))
            },
            style = RunkkTextStyles.BodyStrong,
            color = colors.onSurface
        )
        if (goal != null && goal.meters > 0) {
            RunkkProgressBar(
                last.total.meters.toFloat() / goal.meters,
                color = colors.primary,
                trackColor = colors.outlineVariant
            )
        }
        Text(
            stringResource(R.string.crew_goal_set_participants, last.contributors.count { it.distance.meters > 0 }),
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant
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

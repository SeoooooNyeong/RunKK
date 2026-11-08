package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.runbody.RunBodyRunScore
import com.a207.runkk.app.ui.record.R
import kotlin.math.abs

/**
 * 러닝 선택(G-30): 최근 기록을 최신순으로 보이고 누르면 그 기록을 골라 러닝별 탭으로 돌아온다. 오른쪽에 러닝 점수와 직전 점수 있는 러닝
 * 대비 변화("78 ▲ 6", 계약 v1.2 7절)를 보인다. 점수가 없으면(분석 전, 읽기 실패) 그 칸을 비운다.
 */
@Composable
internal fun RunBodyPickPage(state: RunBodyUiState, actions: RunBodyActions) {
    RunBodyFrame(stringResource(R.string.record_runbody_pick_title), onBack = actions.onBack, onCoach = null) {
        state.runs.forEach { run ->
            val isSelected = run.recordId == state.selectedRecordId
            val km = RunkkFormat.kmShort(run.distance)
            RunkkCard(
                Modifier.fillMaxWidth(),
                shape = RunkkShapes.ExtraLarge,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                onClick = { actions.onPick(run) },
                borderColor = if (isSelected) RunkkColors.Lime else RunkkColors.Border
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            runTime(run.startedAt, state.zone),
                            style = RunkkTextStyles.text(16, FontWeight.Bold),
                            color = RunkkColors.TextPrimary
                        )
                        Text(
                            if (isSelected) {
                                stringResource(R.string.record_runbody_pick_selected, km)
                            } else {
                                stringResource(R.string.record_runbody_pick_row, km, RunkkFormat.duration(run.elapsed))
                            },
                            style = RunkkTextStyles.Label,
                            color = RunkkColors.TextSecondary
                        )
                    }
                    state.scores[run.recordId]?.let { RunScore(it) }
                }
            }
        }
    }
}

/** "러닝 점수" + "78 ▲ 6". 보조 기술은 "러닝 점수 78점, 6점 올랐어요"로 읽는다. 점수가 없으면 그리지 않는다. */
@Composable
private fun RunScore(score: RunBodyRunScore) {
    val value = score.score ?: return
    val change = score.change?.takeIf { it != 0 }
    val spokenChange = when {
        change == null -> null
        change > 0 -> stringResource(R.string.record_runbody_pick_score_up_spoken, change)
        else -> stringResource(R.string.record_runbody_pick_score_down_spoken, -change)
    }
    val spoken = listOfNotNull(stringResource(R.string.record_runbody_pick_score_spoken, value), spokenChange)
        .joinToString(", ")
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(
            stringResource(R.string.record_runbody_pick_score),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$value", style = RunkkTextStyles.text(17, FontWeight.Black), color = RunkkColors.TextPrimary)
            if (change != null) {
                Text(
                    stringResource(
                        if (change >
                            0
                        ) {
                            R.string.record_runbody_pick_score_up
                        } else {
                            R.string.record_runbody_pick_score_down
                        },
                        abs(change)
                    ),
                    style = RunkkTextStyles.text(14, FontWeight.Bold),
                    color = if (change > 0) RunkkColors.Violet else RunkkColors.TextSecondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

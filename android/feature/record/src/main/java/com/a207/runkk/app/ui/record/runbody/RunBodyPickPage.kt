package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.runbody.RunBodyRunScore
import com.a207.runkk.app.ui.record.R
import kotlin.math.abs

/**
 * 러닝 선택(G-30): 최근 기록을 최신순으로 보이고 누르면 그 기록을 골라 러닝별 탭으로 돌아온다. 칸은 흰 카드(모서리 20, 높이 96, 테두리 없음)에
 * 날짜(16 Medium)와 메타 줄(거리, 선택됨 또는 운동 시간)이다. 피그마 "5.2km · 선택됨"의 가운뎃점은 메타 줄로 그린다(D-642).
 * 오른쪽에 러닝 점수와 직전 점수 있는 러닝 대비 변화("78 ▲ 6", 계약 v1.2 7절)를 보인다. 점수가 없으면(분석 전, 읽기 실패) 그 칸을 비운다.
 */
@Composable
internal fun RunBodyPickPage(state: RunBodyUiState, actions: RunBodyActions) {
    RunBodyFrame(stringResource(R.string.record_runbody_pick_title), onBack = actions.onBack, onCoach = null) {
        state.runs.forEach { run ->
            val isSelected = run.recordId == state.selectedRecordId
            val km = MetaItem(
                RunkkIcons.DirectionsRun,
                stringResource(R.string.record_km_value, RunkkFormat.kmShort(run.distance))
            )
            RunkkCard(
                // 라임 테두리가 없어져 고른 칸을 보조 기술에 선택 상태로 알린다
                Modifier.fillMaxWidth().heightIn(min = PICK_ROW_HEIGHT).semantics { selected = isSelected },
                shape = RunkkShapes.Large,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                onClick = { actions.onPick(run) },
                verticalArrangement = Arrangement.Center
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            runTime(run.startedAt, state.zone),
                            style = RunkkTextStyles.text(16, FontWeight.Medium),
                            color = RunkkColors.TextPrimary
                        )
                        MetaRow(
                            listOf(
                                km,
                                if (isSelected) {
                                    MetaItem(
                                        RunkkIcons.CheckCircle,
                                        stringResource(R.string.record_runbody_pick_selected)
                                    )
                                } else {
                                    MetaItem(RunkkIcons.Schedule, RunkkFormat.duration(run.elapsed))
                                }
                            )
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.widthIn(min = SCORE_WIDTH).clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(
            stringResource(R.string.record_runbody_pick_score),
            style = RunkkTextStyles.text(12),
            color = RunkkColors.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$value", style = SCORE_STYLE, color = RunkkColors.TextPrimary)
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
                    style = SCORE_STYLE,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private val PICK_ROW_HEIGHT = 96.dp
private val SCORE_WIDTH = 84.dp

/** 피그마 "78 · ▲ 6" 16 Bold 한 덩어리. 가운뎃점 대신 8 띄운다. */
private val SCORE_STYLE = RunkkTextStyles.text(16, FontWeight.Bold)

package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.ui.record.R

/**
 * 러닝 선택(G-30): 최근 기록을 최신순으로 보이고 누르면 그 기록을 골라 러닝별 탭으로 돌아온다. 피그마의 "러닝 점수 78 ▲6"은
 * 결과를 서버에 저장하지 않아(D-442) 목록 한 번에 받을 수 없어 그리지 않는다(계약 4절).
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
                borderColor = if (isSelected) RunkkColors.Lime else RunkkColors.Border,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
        }
    }
}

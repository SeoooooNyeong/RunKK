package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs

/**
 * 기록런 탭(D-1·E-1, D-250): 셀프런 | 셀럽런 분절 하나. 고른 분절은 rememberSaveable로 지킨다. 분절 안은 기존 화면
 * (SelfBattleRoute·CelebBattleRoute와 그 ViewModel)을 그대로 쓴다. 분절 안 화면 모양은 A3 단계가 디자인에 맞춘다.
 */
@Composable
fun RecordRunRoute() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkSegmentedTabs(
            options = listOf(
                stringResource(R.string.battle_record_run_self),
                stringResource(R.string.battle_record_run_celeb)
            ),
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (selected == 0) SelfBattleRoute() else CelebBattleRoute()
        }
    }
}

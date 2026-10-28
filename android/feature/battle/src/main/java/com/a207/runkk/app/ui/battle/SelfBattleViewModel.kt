package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 셀프 탭: 나와의 싸움(거리별 본인 최고 기록, FR-096). */
@HiltViewModel
class SelfBattleViewModel @Inject constructor(battles: BattleRepository, coordinator: BattleRunCoordinator) :
    RecordBattleViewModel(battles, coordinator, OpponentKind.SELF) {
    init {
        load()
    }

    override suspend fun fetch() = battles.selfCandidates()

    override suspend fun begin(candidate: CandidateUi) = battles.startSelfBattle(candidate.distance)
}

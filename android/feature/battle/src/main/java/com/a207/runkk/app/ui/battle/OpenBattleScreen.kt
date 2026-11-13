package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 열린 대결 회의 진행 화면 키(앱 범위, S15P31A207-278). 최근 앱 목록에서 앱을 밀어 닫으면 Activity만 사라지고 조정자와 측정은 살아
 * 있으므로, 다시 열 때 이 키로 그 진행 화면에 돌아간다. 키의 표시 값(상대 종류, 셀프·셀럽 기록, 배치고사 판 등)은 조정자 상태에 없어
 * 진행 화면이 bind할 때 적어 둔다. 조정자가 그 대결 회를 열고 있는 동안만 돌려준다(닫힌 회, 프로세스가 다시 떠 복원된 키는 null).
 */
@Singleton
class OpenBattleScreen @Inject constructor(private val coordinator: BattleRunCoordinator) {
    @Volatile
    private var last: Routes.BattleRunning? = null

    fun remember(key: Routes.BattleRunning) {
        last = key
    }

    /** 조정자가 열고 있는 회의 진행 화면 키. 없으면 null. */
    fun key(): Routes.BattleRunning? = last?.takeIf { coordinator.isRunning(it.battleId) }
}

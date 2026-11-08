package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.ActiveBattle

/**
 * 서버에 남은 진행 중 대결 안내. [battle]이 있으면 대화상자를 띄운다. [error]는 그만두기 실패 문구다.
 * [returnTo]가 있으면 이 프로세스가 아직 달리고 있는 대결의 진행 화면으로 돌아가기를 안내한다(S15P31A207-278).
 */
data class ActiveBattleUiState(
    val battle: ActiveBattle? = null,
    val isForfeiting: Boolean = false,
    val error: UiText? = null,
    val returnTo: Routes.BattleRunning? = null
)

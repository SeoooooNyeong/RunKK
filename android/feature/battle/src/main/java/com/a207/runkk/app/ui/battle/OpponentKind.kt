package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes

/**
 * 대결 상대 종류(화면 문구용). Routes.BattleRunning.opponentKind 값이다. 고스트는 랜덤으로 보인다(Q-02).
 * 진행 중 그만두기 확인 문구(제목·본문·확인 버튼)는 종류마다 여기에 둔다(D-266). 취소 버튼은 모두 "계속 달리기"다.
 * 셀프런·셀럽런은 그만두면 기록을 버리고, 랜덤·친구 대결은 패배로 기록된다.
 */
enum class OpponentKind(
    @StringRes val forfeitTitle: Int,
    @StringRes val forfeitMessage: Int,
    @StringRes val forfeitConfirm: Int
) {
    RANDOM(
        R.string.battle_running_forfeit_title,
        R.string.battle_running_forfeit_message,
        R.string.battle_running_forfeit_confirm
    ),
    FRIEND(
        R.string.battle_running_forfeit_title,
        R.string.battle_running_forfeit_message,
        R.string.battle_running_forfeit_confirm
    ),
    SELF(
        R.string.battle_forfeit_title_self,
        R.string.battle_forfeit_message_self,
        R.string.battle_forfeit_confirm_quit
    ),
    CELEBRITY(
        R.string.battle_forfeit_title_celeb,
        R.string.battle_forfeit_message_celeb,
        R.string.battle_forfeit_confirm_quit
    );

    /** 기록 상대(과거의 나·셀럽). 그만두면 기록을 버리고 결과 화면이 없다(D-266). */
    val isRecord: Boolean get() = this == SELF || this == CELEBRITY

    /** 실제 사람과 함께 달리는 대결(랜덤·고스트·친구). 상대 이름에 "님"을 붙인다(D-283). */
    val isPerson: Boolean get() = this == RANDOM || this == FRIEND

    companion object {
        fun of(name: String): OpponentKind = entries.firstOrNull { it.name == name } ?: RANDOM
    }
}

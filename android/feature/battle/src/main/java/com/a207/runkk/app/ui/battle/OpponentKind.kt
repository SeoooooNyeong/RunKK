package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes

/**
 * 대결 상대 종류(화면 문구용). Routes.BattleRunning.opponentKind 값이다. 고스트는 랜덤으로 보인다(Q-02).
 * 진행 중 그만두기 확인 문구(제목·본문·확인 버튼)는 종류마다 여기에 둔다(D-266). 취소 버튼은 모두 "계속 달리기"다.
 * 그만두기는 모두 패배로 판정하고 달린 기록을 남긴다. 셀프런·셀럽런은 따로 취소([isCancellable], 기록 없음)가 있다(2026-10-30).
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

    /** 기록 상대(과거의 나·셀럽). */
    val isRecord: Boolean get() = this == SELF || this == CELEBRITY

    /** 진행 중 취소(기록 없이 끝내기) 버튼이 있는가: 기록 상대 대결만(사용자 요청 2026-10-30). */
    val isCancellable: Boolean get() = isRecord

    /** 실제 사람과 함께 달리는 대결(랜덤·고스트·친구). 상대 이름에 "님"을 붙인다(D-283). */
    val isPerson: Boolean get() = this == RANDOM || this == FRIEND

    companion object {
        fun of(name: String): OpponentKind = entries.firstOrNull { it.name == name } ?: RANDOM
    }
}

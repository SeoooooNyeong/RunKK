package com.a207.runkk.app.ui.battle

/**
 * 대결 상대 종류(화면 문구용). Routes.BattleRunning.opponentKind 값이다. 고스트는 랜덤으로 보인다(Q-02).
 */
enum class OpponentKind {
    RANDOM,
    SELF,
    CELEBRITY;

    companion object {
        fun of(name: String): OpponentKind = entries.firstOrNull { it.name == name } ?: RANDOM
    }
}

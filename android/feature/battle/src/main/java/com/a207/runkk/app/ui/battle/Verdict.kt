package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes

/** 판정(서버 battle.Verdict). */
enum class Verdict(@StringRes val labelRes: Int, @StringRes val headlineRes: Int) {
    WIN(R.string.battle_verdict_win, R.string.battle_result_headline_win),
    LOSE(R.string.battle_verdict_lose, R.string.battle_result_headline_lose),
    DRAW(R.string.battle_verdict_draw, R.string.battle_result_headline_draw);

    companion object {
        fun of(value: String?): Verdict? = entries.firstOrNull { it.name == value }
    }
}

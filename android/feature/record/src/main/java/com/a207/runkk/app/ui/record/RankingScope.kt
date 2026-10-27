package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes

enum class RankingScope(@StringRes val labelRes: Int) {
    PERSONAL(R.string.record_ranking_scope_personal),
    CREW(R.string.record_ranking_scope_crew)
}

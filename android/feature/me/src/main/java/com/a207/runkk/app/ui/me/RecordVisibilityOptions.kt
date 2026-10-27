package com.a207.runkk.app.ui.me

/** 기록 공개 범위(FR-010·FR-028, 서버 RecordVisibility 이름). 기본 전체공개. */
object RecordVisibilityOptions {
    val values: List<Pair<String, Int>> = listOf(
        "PUBLIC" to R.string.me_choice_record_public,
        "CREW" to R.string.me_choice_record_crew,
        "PRIVATE" to R.string.me_choice_private
    )
}

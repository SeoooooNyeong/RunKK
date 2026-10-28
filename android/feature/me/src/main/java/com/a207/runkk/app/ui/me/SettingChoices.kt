package com.a207.runkk.app.ui.me

/** 설정 화면 선택지: 서버 enum 이름(서버로 가는 값) → 화면 문구 리소스. 문구는 화면에서 푼다. */
object SettingChoices {
    /** 서버 DistanceUnit. */
    val unit: List<Pair<String, Int>> = listOf("KM" to R.string.me_choice_unit_km, "MI" to R.string.me_choice_unit_mile)

    /** 서버 ProfileVisibility. */
    val profileVisibility: List<Pair<String, Int>> =
        listOf("PUBLIC" to R.string.me_choice_public, "PRIVATE" to R.string.me_choice_private)

    /** 서버 ScreenTheme(FR-203): 기기 설정 따름·라이트·다크. */
    val theme: List<Pair<String, Int>> = listOf(
        "SYSTEM" to R.string.me_choice_theme_system,
        "LIGHT" to R.string.me_choice_theme_light,
        "DARK" to R.string.me_choice_theme_dark
    )
}

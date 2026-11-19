package com.a207.runkk.app.core.navigation

/**
 * 경로 화면이 들어올 때의 넘어가기(S15P31A207-335 8회차). 기능 모듈은 경로를 등록할 때 addEntryProvider(metadata = [INSTANT])로 붙이고,
 * app의 화면 그래프(NavDisplay)가 [isInstant]를 보고 앞 화면과 엇갈려 나타나는 기본 넘어가기(0.7초) 없이 바로 그린다.
 * 기능 모듈은 navigation3-ui를 보지 않으므로 표지는 이 패키지의 값이다.
 */
object EntryTransition {
    private const val KEY = "runkk.entry.transition"
    private const val INSTANT_VALUE = "instant"

    /** 들어오자마자 다 보여야 하는 화면(대결 진행: 출발 카운트다운이 늦게 보이면 안 된다). */
    val INSTANT: Map<String, Any> = mapOf(KEY to INSTANT_VALUE)

    /** [metadata]가 바로 그리는 경로의 것인가. */
    fun isInstant(metadata: Map<String, Any>): Boolean = metadata[KEY] == INSTANT_VALUE
}

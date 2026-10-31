package com.a207.runkk.app.domain.runimal

/**
 * 16유형 목록(GET /runimal/types, D-285). [current]는 지금 유형(없으면 null), 항목마다 [Entry.isEverConfirmed]는 이 러너가
 * 그 유형을 한 번이라도 확정받았는가(흐림 판정)다. [Entry.axes]는 축 이름(COMPANION·DISTANCE·COURSE·HABIT) → 기운 쪽.
 */
data class RunimalTypeBoard(val current: String?, val types: List<Entry>) {
    data class Entry(
        val type: String,
        val displayName: String,
        val icon: String?,
        val description: String,
        val axes: Map<String, AxisLean>,
        val isEverConfirmed: Boolean
    )
}

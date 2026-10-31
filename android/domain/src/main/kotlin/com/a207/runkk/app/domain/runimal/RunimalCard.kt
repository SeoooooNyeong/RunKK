package com.a207.runkk.app.domain.runimal

/**
 * Runimal 유형 카드(서버 runimal.RunimalCardView, 앞면만, FR-168). axisRatios는 축별 우세 비율이다.
 * [axes]는 축 막대(축 이름·기운 쪽·그쪽 비율 %, D-285)이고 옛 서버면 빈 목록이다.
 */
data class RunimalCard(
    val type: String,
    val title: String,
    val description: String,
    val axisRatios: Map<String, Double>,
    val axes: List<AxisBar> = emptyList()
) {
    /** 축 막대 하나("솔로런 78%"). */
    data class AxisBar(val axis: String, val lean: AxisLean, val percent: Int)
}

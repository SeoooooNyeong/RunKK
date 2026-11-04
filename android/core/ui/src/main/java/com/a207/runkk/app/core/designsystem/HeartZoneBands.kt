package com.a207.runkk.app.core.designsystem

import java.time.Duration

/**
 * 심박 구간 4단계(FR-289, J24-17, D-503). 서버 Z1~Z5를 Z1·Z2·Z3 그대로, Z4+Z5를 Z4로 합친다.
 * 비율은 정수 %이고 합이 100이 되게 반올림 나머지를 가장 큰 칸(동률이면 앞 단계)에서 맞춘다. 모르는 키는 무시하고,
 * 시간이 없으면(빈 맵·모두 0초) 빈 목록이다.
 */
object HeartZoneBands {
    private val GROUPS: List<Pair<String, List<String>>> = listOf(
        "Z1" to listOf("Z1"),
        "Z2" to listOf("Z2"),
        "Z3" to listOf("Z3"),
        "Z4" to listOf("Z4", "Z5")
    )

    fun toFour(zones: Map<String, Duration>): List<ZoneShare> {
        val seconds = GROUPS.map { (_, keys) -> keys.sumOf { zones[it]?.seconds?.coerceAtLeast(0) ?: 0L } }
        val total = seconds.sum()
        if (total <= 0) return emptyList()
        val percents = seconds.map { Math.round(it * PERCENT.toDouble() / total).toInt() }.toMutableList()
        val largest = percents.indices.maxBy { percents[it] }
        percents[largest] += PERCENT - percents.sum()
        return GROUPS.mapIndexed { index, (label, _) -> ZoneShare(label, percents[index]) }
    }

    private const val PERCENT = 100
}

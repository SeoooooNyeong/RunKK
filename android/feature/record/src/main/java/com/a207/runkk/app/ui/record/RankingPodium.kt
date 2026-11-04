package com.a207.runkk.app.ui.record

import com.a207.runkk.app.domain.record.Ranking

/**
 * 랭킹 TOP3 시상대와 4위부터의 목록(FR-290, J24-15, 피그마 61296:3779). 앞 세 행이 시상대(가운데 1위, 왼쪽 2위, 오른쪽 3위)이고
 * 나머지가 목록이다. 받침 숫자는 행의 rank 그대로라 동순위(1·2·2)도 그대로 보인다(FR-070). 행이 셋보다 적으면 빈 자리는 null이다.
 */
data class RankingPodium(
    val first: Ranking.Entry?,
    val second: Ranking.Entry?,
    val third: Ranking.Entry?,
    val rest: List<Ranking.Entry>
) {
    companion object {
        private const val PODIUM_SIZE = 3

        fun of(entries: List<Ranking.Entry>): RankingPodium = RankingPodium(
            first = entries.getOrNull(0),
            second = entries.getOrNull(1),
            third = entries.getOrNull(2),
            rest = entries.drop(PODIUM_SIZE)
        )
    }
}

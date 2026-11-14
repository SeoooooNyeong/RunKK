package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.domain.live.CrewRunRelay
import kotlin.math.abs

/**
 * 크루런 트랙 보기의 내 순위와 가장 가까운 사람, 앞뒤 인원수([임시:D-성-267], [임시:FR-성-30]). 화면 표시에만 쓰고 결과나 보상에는 쓰지
 * 않는다. [rank]는 1 + 나보다 멀리 간 사람 수(같은 거리는 같은 순위), [total]은 나를 더한 참가 인원이다. [nearest]는 트랙 위 레인에 그릴
 * 한 사람이고 [nearestGapMeters]는 그 사람 − 나(m, 앞이면 양수)다. [ahead]·[behind]는 가장자리 칩의 인원(가장 가까운 사람은 뺀다)이다.
 */
data class CrewRunStanding(
    val rank: Int,
    val total: Int,
    val nearest: Mate?,
    val nearestGapMeters: Int?,
    val ahead: Int,
    val behind: Int
) {
    /** 나를 뺀 참가자 한 명(중계 한 줄). [meters]는 누적 거리, [state]는 중계 상태다. */
    data class Mate(val memberId: Long?, val name: String, val meters: Int, val state: CrewRunRelay.State)

    companion object {
        /** 중계를 받기 전(나 혼자). */
        val ALONE = CrewRunStanding(rank = 1, total = 1, nearest = null, nearestGapMeters = null, ahead = 0, behind = 0)

        /**
         * [mates]는 나를 뺀 참가자(대기실 입장 순)다. 가장 가까운 사람은 |거리 − 내 거리|가 가장 작은 사람이고, 같으면 앞선 쪽, 그다음은
         * 입장 순이다. 후보는 달리는 중이거나 일시정지인 사람뿐이다(끝냈거나 연결 확인 중인 사람은 레인에 그리지 않는다).
         */
        fun of(myMeters: Int, mates: List<Mate>): CrewRunStanding {
            val nearest = mates
                .filter { it.state == CrewRunRelay.State.RUNNING || it.state == CrewRunRelay.State.PAUSED }
                .minWithOrNull(compareBy<Mate> { abs(it.meters - myMeters) }.thenBy { if (it.meters > myMeters) 0 else 1 })
            val gap = nearest?.let { it.meters - myMeters }
            val aheadAll = mates.count { it.meters > myMeters }
            val behindAll = mates.count { it.meters < myMeters }
            return CrewRunStanding(
                rank = 1 + aheadAll,
                total = mates.size + 1,
                nearest = nearest,
                nearestGapMeters = gap,
                ahead = aheadAll - if (gap != null && gap > 0) 1 else 0,
                behind = behindAll - if (gap != null && gap < 0) 1 else 0
            )
        }
    }
}

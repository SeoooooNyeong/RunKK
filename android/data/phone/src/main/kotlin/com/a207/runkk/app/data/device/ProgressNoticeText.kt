package com.a207.runkk.app.data.device

import android.content.Context
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.data.phone.R
import com.a207.runkk.app.domain.run.FreeRunGoalState
import com.a207.runkk.app.domain.run.ProgressNotice
import java.time.Duration
import java.util.Locale
import kotlin.math.abs

/**
 * 진행 알림 한 장의 문구(D-259). 두 알림 표현(LiveUpdateStyle·OngoingProgressStyle)이 같은 제목·본문을 쓰게 여기서 한 번 만든다.
 * [chip]은 상태 표시줄 칩 글자(자유런·크루런 거리 "2.14 km", 대결 거리 차 "+42 m"), [action]은 자유런·크루런에 있는 동작 버튼이다.
 * [progressMax]가 null이면 진행 막대를 그리지 않는다(목표 자유). [battleId]는 대결 알림을 눌렀을 때 돌아갈 대결이다(S15P31A207-278).
 */
internal data class ProgressNoticeText(
    val title: String,
    val distance: String,
    val detail: String?,
    val chip: String,
    val action: Action?,
    val progress: Int?,
    val progressMax: Int?,
    val battleId: Long? = null
) {
    /** 본문 한 줄: 거리와 목표·거리 차 줄. */
    val body: String
        get() = listOfNotNull(distance, detail).joinToString(BODY_GAP)

    /** 알림 동작 버튼(자유런·크루런). */
    enum class Action { PAUSE, RESUME }

    companion object {
        private const val BODY_GAP = "   "
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3_600L

        fun of(context: Context, notice: ProgressNotice): ProgressNoticeText {
            val distance = context.getString(R.string.progress_notice_distance, km(notice.distance))
            val end = notice.progressEnd?.meters?.takeIf { it > 0 }
            val progress = end?.let { notice.distance.meters.coerceAtMost(it).toInt() }
            return when (notice) {
                is ProgressNotice.Free -> ProgressNoticeText(
                    title = context.getString(R.string.progress_notice_free_title, elapsedLabel(notice.elapsed)),
                    distance = distance,
                    detail = goalLine(context, notice.goal),
                    chip = distance,
                    action = if (notice.isPaused) Action.RESUME else Action.PAUSE,
                    progress = progress,
                    progressMax = end?.toInt()
                )

                is ProgressNotice.CrewRun -> ProgressNoticeText(
                    title = context.getString(R.string.progress_notice_crew_run_title, elapsedLabel(notice.elapsed)),
                    distance = distance,
                    detail = null,
                    chip = distance,
                    action = if (notice.isPaused) Action.RESUME else Action.PAUSE,
                    progress = null,
                    progressMax = null
                )

                is ProgressNotice.Battle -> ProgressNoticeText(
                    title = context.getString(
                        R.string.progress_notice_battle_title,
                        notice.opponentName,
                        elapsedLabel(notice.elapsed)
                    ),
                    distance = distance,
                    detail = notice.gapMeters?.let { gapLine(context, it) },
                    chip = notice.gapMeters?.let { gapChip(context, it) } ?: distance,
                    action = null,
                    progress = progress,
                    progressMax = end?.toInt(),
                    battleId = notice.battleId
                )
            }
        }

        private fun goalLine(context: Context, goal: FreeRunGoalState): String? = when (goal) {
            FreeRunGoalState.None -> null

            FreeRunGoalState.Reached -> context.getString(R.string.progress_notice_goal_reached)

            is FreeRunGoalState.Remaining -> context.getString(
                R.string.progress_notice_goal_remaining,
                km(goal.distance)
            )
        }

        /** 거리 차 줄. 0은 앞서는 쪽으로 보인다(TC-9606 제안). */
        private fun gapLine(context: Context, gap: Int): String = if (gap >= 0) {
            context.getString(R.string.progress_notice_ahead, gap)
        } else {
            context.getString(R.string.progress_notice_behind, abs(gap))
        }

        private fun gapChip(context: Context, gap: Int): String = if (gap >= 0) {
            context.getString(R.string.progress_notice_gap_ahead_chip, gap)
        } else {
            context.getString(R.string.progress_notice_gap_behind_chip, abs(gap))
        }

        /** 경과 "11:52", 한 시간이 넘으면 "1:02:10". */
        fun elapsedLabel(elapsed: Duration): String {
            val total = elapsed.seconds.coerceAtLeast(0)
            val h = total / SECONDS_PER_HOUR
            val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
            val s = total % SECONDS_PER_MINUTE
            return if (h > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.ROOT, "%02d:%02d", m, s)
            }
        }

        /** 소수 둘째 자리 km("2.14"). */
        fun km(d: Distance): String = String.format(Locale.ROOT, "%.2f", d.km())
    }
}

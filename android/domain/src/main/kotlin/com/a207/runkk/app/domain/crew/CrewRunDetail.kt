package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import java.time.DayOfWeek
import java.time.Instant

/**
 * 크루런 일정 상세(서버 CrewScheduleDtos.CrewRunDetailResponse, 피그마 F-13·F-11d). [distance]가 null이면 제한 없음,
 * [myAnswer]가 null이면 응답하지 않았거나 참석을 취소했다. [repeat]이 WEEKLY면 "매주" 배지이고 [repeatDay]가 그 요일이다.
 * [attendees]는 참석자이고 작성자가 참석 중이면 맨 앞이다. [status]가 앱이 모르는 값이면 null이다.
 */
data class CrewRunDetail(
    val postId: Long,
    val crewId: Long,
    val title: String,
    val body: String,
    val authorName: String,
    val isMine: Boolean,
    val writtenAt: Instant,
    val scheduledAt: Instant,
    val distance: Distance?,
    val status: CrewRunStatus?,
    val goingCount: Int,
    val myAnswer: AttendanceAnswer?,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val repeat: CrewRunRepeat,
    val repeatDay: DayOfWeek?,
    val attendees: List<Attendee>
) {
    /** 내가 참석 중이다(하단 버튼 "참석 취소하기"). */
    val isGoing: Boolean get() = myAnswer == AttendanceAnswer.GOING

    /** 끝났거나 취소된 일정(F-11d 지난 일정 상세: 하단 버튼 없음). */
    val isPast: Boolean get() = status == CrewRunStatus.FINISHED || status == CrewRunStatus.CANCELLED

    /**
     * [memberId]는 탈퇴했으면 null, [isAuthor]는 "작성자" 꼬리표, [isMe]는 "나". [distance]는 마친 크루런에서 달린 거리(F-11d, FR-329),
     * 마치지 않았거나 달리지 않았으면 null.
     */
    data class Attendee(
        val memberId: Long?,
        val displayName: String,
        val isAuthor: Boolean,
        val isMe: Boolean,
        val faceKey: String? = null,
        val shownGender: Gender? = null,
        val distance: Distance? = null
    )
}

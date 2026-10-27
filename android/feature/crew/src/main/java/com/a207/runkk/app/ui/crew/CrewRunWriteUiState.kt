package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.PostList
import java.time.LocalDate
import java.time.LocalTime

/**
 * 일정 쓰기(FR-132) 상태. [kind]가 CREW_RUN이면 크루런(제목·거리, 장소 칸 없음), MEETUP이면 대면 모임(내용·장소 1~50자·
 * 정원 2~10, 비우면 크루 정원)이다. 작성자는 서버가 참석으로 넣는다(D-151).
 */
data class CrewRunWriteUiState(
    val kind: PostList.Kind = PostList.Kind.CREW_RUN,
    val title: String = "",
    val placeName: String = "",
    /** 대면 모임 정원(작성자 포함). null이면 크루 정원(10). */
    val capacity: Int? = null,
    val body: String = "",
    val distance: Distance = Distance(5_000),
    /** 오늘부터 며칠 뒤(0~6). */
    val dayOffset: Int = 1,
    val time: LocalTime = LocalTime.of(DEFAULT_HOUR, 0),
    val today: LocalDate = LocalDate.MIN,
    val isSaving: Boolean = false,
    val error: UiText? = null
) {
    val isValid: Boolean
        get() = if (kind == PostList.Kind.MEETUP) {
            body.isNotBlank() && placeName.isNotBlank()
        } else {
            title.isNotBlank()
        }

    companion object {
        const val DEFAULT_HOUR = 7
        const val PLACE_MAX = 50
        const val CAPACITY_MIN = 2
        const val CAPACITY_MAX = 10
        const val DAYS = 7
        val TIMES: List<LocalTime> = (5..23).flatMap { h -> listOf(LocalTime.of(h, 0), LocalTime.of(h, 30)) }
    }
}

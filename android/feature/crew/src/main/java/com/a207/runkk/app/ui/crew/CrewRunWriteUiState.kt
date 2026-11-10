package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import java.time.LocalDate
import java.time.LocalTime

/** 모집하기(크루 ID로 새 글) 또는 수정(모집글 ID, 피그마 F-13d). */
sealed interface CrewRunWriteMode {
    data class Create(val crewId: Long) : CrewRunWriteMode

    data class Edit(val postId: Long) : CrewRunWriteMode
}

/** 작성 화면에서 연 바텀시트(피그마 F-12b 날짜·F-12d 시간·F-12e 거리·F-12f 반복). */
enum class CrewRunWriteSheet { DATE, TIME, DISTANCE, REPEAT }

/**
 * 크루런 모집하기·수정(FR-132, 피그마 F-12·F-13d) 상태. 칸은 제목 → 내용 → 날짜 → 시간 → 거리 → 반복 순이고 날짜·시간·거리는 처음에
 * 비어 있다(자리 문구). 날짜는 오늘~14일 뒤([DAYS]일), 시간은 [MINUTE_STEP]분 단위다(서버 규칙과 같다). 수정이면 날짜·시간을 잠근다.
 * [legacyDistance]는 수정할 글의 거리가 선택지 밖(기존 1km)일 때 그 값이고, 다른 선택지를 고르면 비운다.
 */
data class CrewRunWriteUiState(
    val mode: CrewRunWriteMode? = null,
    val title: String = "",
    val body: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val distance: CrewRunDistanceChoice? = null,
    val legacyDistance: Distance? = null,
    val repeat: CrewRunRepeat = CrewRunRepeat.NONE,
    val today: LocalDate = LocalDate.MIN,
    val sheet: CrewRunWriteSheet? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiText? = null,
    val loadError: UiText? = null
) {
    val isEdit: Boolean get() = mode is CrewRunWriteMode.Edit

    /** 거리를 골랐다(선택지 또는 기존 1km 유지). */
    val hasDistance: Boolean get() = distance != null || legacyDistance != null

    /** 보낼 거리: 선택지 거리, 없으면 기존 거리. 제한 없음은 null. */
    val distanceToSend: Distance? get() = distance?.distance ?: legacyDistance

    /** 올리기·저장을 누를 수 있다: 제목, 날짜, 시간, 거리를 다 채웠다. */
    val isValid: Boolean
        get() = title.isNotBlank() && date != null && time != null && hasDistance && !isLoading && loadError == null

    /** 고를 수 있는 마지막 날(오늘 포함 [DAYS]일). */
    val lastDate: LocalDate get() = today.plusDays(DAYS - 1L)

    companion object {
        /** 고를 수 있는 날 수(오늘 포함, 피그마 F-12b). */
        const val DAYS = 15

        /** 시간 단위(분, 피그마 F-12d). */
        const val MINUTE_STEP = 5

        /** 시간 휠이 처음 보이는 값(시간을 아직 고르지 않았을 때). */
        val DEFAULT_TIME: LocalTime = LocalTime.of(7, 0)
    }
}

package com.a207.runkk.app.ui.crew

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkTagCategory
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 크루 태그 두 줄(평일, 주말) × 시간대 넷(피그마 F-01b·F-04). */
internal val CREW_TAG_CATEGORIES: List<RunkkTagCategory> = RunkkTags.categories

internal val JoinPolicy.labelRes: Int
    @StringRes get() = when (this) {
        JoinPolicy.APPROVAL -> R.string.crew_join_policy_approval
        JoinPolicy.FREE -> R.string.crew_join_policy_free
    }

/** 크루 거부 사유 문구. 키는 서버 common.ReasonCode의 크루 사유(5.1~5.7)와 PERMISSION_DENIED(403)이고, 나머지는 공용 문구다. */
internal object CrewErrorText {
    fun of(error: AppError): UiText {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get)?.let { UiText.Res(it) } ?: RunkkErrorText.of(error)
    }

    internal val REJECTED: Map<String, Int> = mapOf(
        "CREW_NOT_FOUND" to R.string.crew_error_crew_not_found,
        "CREW_FULL" to R.string.crew_error_crew_full,
        "ALREADY_IN_CREW" to R.string.crew_error_already_in_crew,
        "COOLDOWN" to R.string.crew_error_cooldown,
        "CREW_NAME_INVALID" to R.string.crew_error_crew_name_invalid,
        "CREW_NAME_TAKEN" to R.string.crew_error_crew_name_taken,
        "TOO_MANY_ATTEMPTS" to R.string.crew_error_too_many_attempts,
        "INVALID_CODE" to R.string.crew_error_invalid_code,
        "ALREADY_REQUESTED" to R.string.crew_error_already_requested,
        "JOIN_REQUEST_NOT_FOUND" to R.string.crew_error_join_request_not_found,
        "NOT_CANCELLABLE" to R.string.crew_error_not_cancellable,
        "REQUEST_DECIDED" to R.string.crew_error_request_decided,
        "PERMISSION_DENIED" to R.string.crew_error_permission_denied,
        "LEADER_MUST_TRANSFER" to R.string.crew_error_leader_must_transfer,
        "CREW_DELETE_REQUIRED" to R.string.crew_error_crew_delete_required,
        "TARGET_INVALID" to R.string.crew_error_target_invalid,
        "NOT_CREW_MEMBER" to R.string.crew_error_not_crew_member,
        "BODY_TOO_LONG" to R.string.crew_error_body_too_long,
        "CREW_RUN_INVALID" to R.string.crew_error_crew_run_invalid,
        "POST_NOT_FOUND" to R.string.crew_error_post_not_found,
        "VOTE_CLOSED" to R.string.crew_error_vote_closed,
        "LOBBY_NOT_OPEN" to R.string.crew_error_lobby_not_open,
        "ALREADY_STARTED" to R.string.crew_error_already_started,
        "NOT_PARTICIPANT" to R.string.crew_error_not_participant,
        "MANAGER_LIMIT" to R.string.crew_error_manager_limit,
        "PIN_LIMIT" to R.string.crew_error_pin_limit,
        "POLL_CLOSED" to R.string.crew_error_poll_closed,
        "POLL_INVALID" to R.string.crew_error_poll_invalid,
        "SCHEDULE_INVALID" to R.string.crew_error_schedule_invalid,
        "SCHEDULE_FULL" to R.string.crew_error_schedule_full,
        "POST_EMPTY" to R.string.crew_error_post_empty,
        "PHOTO_LIMIT" to R.string.crew_error_photo_limit,
        "TOO_LARGE" to R.string.crew_error_too_large,
        "UNSUPPORTED_IMAGE" to R.string.crew_error_unsupported_image,
        "ALREADY_REPORTED" to R.string.crew_error_already_reported,
        "REPORT_TARGET_NOT_FOUND" to R.string.crew_error_report_target_not_found,
        "GOAL_INVALID" to R.string.crew_error_goal_invalid
    )
}

/** 크루런 상태 문구(서버 crew.CrewRunStatus). */
@StringRes
internal fun crewRunStatusLabelRes(status: CrewRunStatus): Int = when (status) {
    CrewRunStatus.VOTING -> R.string.crew_run_status_voting
    CrewRunStatus.LOBBY_OPEN -> R.string.crew_run_status_lobby_open
    CrewRunStatus.RUNNING -> R.string.crew_run_status_running
    CrewRunStatus.FINISHED -> R.string.crew_run_status_finished
    CrewRunStatus.CANCELLED -> R.string.crew_run_status_cancelled
}

/**
 * 크루런 모집글 한 줄 요약: "5km, 10월 24일(토) 07:00, 참석 3명" (+ 끝났으면 "n명 참여"). 크루런 합산 방식에서 서버
 * finisherCount는 완주 인원이 아니라 함께 달린(진행 참가) 인원이다.
 */
internal fun crewRunSummary(
    resources: Resources,
    info: PostList.CrewRunInfo,
    zone: ZoneId = ZoneId.systemDefault()
): String = listOfNotNull(
    crewRunDistanceLabel(resources, info.distance),
    meetLabel(resources, info.scheduledAt, zone),
    resources.getString(R.string.crew_run_going_count, info.goingCount),
    info.takeIf { it.status == CrewRunStatus.FINISHED }
        ?.let { resources.getString(R.string.crew_run_participant_count, it.finisherCount) }
).joinToString(", ")

/** 화면에서 쓰는 [crewRunSummary]. */
@Composable
internal fun crewRunSummary(info: PostList.CrewRunInfo): String = crewRunSummary(LocalContext.current.resources, info)

/** "10월 24일(토) 07:00". 틀은 문자열 리소스(crew_meet_pattern)다. */
internal fun meetLabel(resources: Resources, at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    at.atZone(zone).format(DateTimeFormatter.ofPattern(resources.getString(R.string.crew_meet_pattern), Locale.KOREAN))

/** 화면에서 쓰는 [meetLabel]. */
@Composable
internal fun meetLabel(at: Instant): String = meetLabel(LocalContext.current.resources, at)

/** "10/21 18:30". */
internal fun postedLabel(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = at.atZone(zone).format(POSTED)

private val POSTED: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)

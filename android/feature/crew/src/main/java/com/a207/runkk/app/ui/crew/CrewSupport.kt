package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkTagCategory
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRunCoordinator
import com.a207.runkk.app.domain.crew.JoinPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 크루 성향 태그 7분류(크루 전용 "크루 분위기" 포함, SRS 3.5.1). */
internal val CREW_TAG_CATEGORIES: List<RunkkTagCategory> = RunkkTags.categories

/** 크루런 거리(FR-132): 1·3·5·10km. */
internal val CREW_RUN_DISTANCES: List<Distance> =
    listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))

internal val JoinPolicy.label: String
    get() = when (this) {
        JoinPolicy.APPROVAL -> "승인제"
        JoinPolicy.FREE -> "바로 가입"
    }

/** 크루 거부 사유 문구. 크루 사유 코드는 10/21 API 계약 확정 전이라 알려진 이름만 두고 나머지는 공용 문구다. */
internal object CrewErrorText {
    fun of(error: AppError): String {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get) ?: RunkkErrorText.of(error)
    }

    private val REJECTED = mapOf(
        "CREW_FULL" to "정원 초과. 크루는 크루장 포함 10명까지예요",
        "ALREADY_IN_CREW" to "이미 크루에 소속돼 있어요. 1인 1크루예요",
        "CREW_COOLDOWN" to "크루를 나온 뒤 24시간 동안은 가입하거나 만들 수 없어요",
        "CREW_NAME_TAKEN" to "이미 있는 크루명이에요",
        "CREW_NAME_INVALID" to "크루명은 2~20자로 입력해 주세요",
        "INVITE_CODE_INVALID" to "초대 코드가 없거나 7일이 지났어요",
        "JOIN_REQUEST_PENDING" to "이 크루에 이미 가입 신청을 했어요",
        "NOT_CREW_LEADER" to "권한 없음. 크루장만 할 수 있어요",
        "FORBIDDEN" to "권한 없음. 크루장만 할 수 있어요",
        "CREW_LEADER_TRANSFER_REQUIRED" to "크루장 양도 필요. 크루원에게 크루장을 넘긴 뒤 탈퇴해 주세요",
        "POST_TOO_LONG" to "응원글은 200자까지 쓸 수 있어요",
        "VOTE_CLOSED" to "투표가 마감됐어요",
        "LOBBY_CLOSED" to "대기실은 시작 30분 전부터 시작 시각까지만 열려요",
        "MEET_AT_PASSED" to "모집 일시가 이미 지났어요",
        "CREW_RUN_DISTANCE_INVALID" to "크루런 거리는 1·3·5·10km 중에서 골라요"
    )
}

/** 게시글 요약에서 크루런 거리(예: "5km")를 찾는다. 게시판 목록에 거리 항목이 생기면 그 값을 쓴다. */
internal fun distanceHint(summary: String): Distance? =
    DISTANCE_IN_TEXT.find(summary)?.groupValues?.get(1)?.toLongOrNull()
        ?.let { Distance(it * METERS_PER_KM) }
        ?.takeIf { it in CREW_RUN_DISTANCES }

private val DISTANCE_IN_TEXT = Regex("""(\d+)\s*(?:km|K)""", RegexOption.IGNORE_CASE)
private const val METERS_PER_KM = 1_000L

/** "10월 24일(토) 07:00". */
internal fun meetLabel(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = at.atZone(zone).format(MEET)

/** "10/21 18:30". */
internal fun postedLabel(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = at.atZone(zone).format(POSTED)

private val MEET: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN)
private val POSTED: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)

// ---------- 통합 브랜치 domain과의 호환 ----------
// 통합 브랜치(feature/runkk-impl, 436ef01)의 CrewRunCoordinator에는 failure·acknowledgeFinished가 있다. 이 작업 트리(9bd7e7c)의
// domain에는 아직 없어 같은 이름의 확장으로 컴파일만 맞춘다. 통합하면 멤버가 확장보다 먼저 골라지므로 아래 두 선언만 지운다.

/** 이 회의 실패(통합 전에는 늘 null). */
internal val CrewRunCoordinator.failure: StateFlow<AppError?> get() = NO_FAILURE

/** 결과 화면이 Finished를 보인 뒤 부른다(통합 전에는 아무것도 하지 않는다). */
@Suppress("UnusedReceiverParameter")
internal fun CrewRunCoordinator.acknowledgeFinished() = Unit

private val NO_FAILURE: StateFlow<AppError?> = MutableStateFlow<AppError?>(null).asStateFlow()

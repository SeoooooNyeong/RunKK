package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.BoardPost
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.Notice
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.Poll
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus

// 도메인 ↔ DTO 매퍼(13.2 toRequest·toDomain). 앱이 모르는 글 종류는 건너뛰고, 모르는 가입 방식은 승인제로 읽는다.

internal fun CrewForm.toRequest(): CrewFormDto = CrewFormDto(name, intro, tags, visibility.name, joinPolicy.name)

/** 서버 모집글에는 제목이 없어 제목과 본문을 줄바꿈으로 이어 body에 싣는다. 모집 일시는 오프셋 시각이다. */
internal fun CrewRunForm.toRequest(): PostWriteRequest = PostWriteRequest(
    kind = PostList.Kind.CREW_RUN.name,
    body = listOfNotNull(title.takeIf { it.isNotBlank() }, body?.takeIf { it.isNotBlank() }).joinToString(NEWLINE),
    distanceMeters = distance.meters,
    scheduledAt = ServerTime.formatWithOffset(meetAt)
)

/** 모르는 공개 범위는 비공개로, 가입 방식은 승인제로, 역할은 크루 밖(null)으로 읽는다(넓게 허용하지 않는 쪽). */
internal fun CrewDetailResponse.toDomain(): CrewDetail = CrewDetail(
    crewId = crewId,
    name = name,
    intro = intro,
    tags = tags,
    visibility = enumByName<CrewVisibility>(visibility) ?: CrewVisibility.PRIVATE,
    joinPolicy = enumByName<JoinPolicy>(joinPolicy) ?: JoinPolicy.APPROVAL,
    logoUrl = logoUrl,
    leaderId = leaderId,
    leaderName = leaderName.orEmpty(),
    headcount = headcount,
    weeklyDistance = Distance(weeklyDistanceMeters),
    myRole = myRole?.let { enumByName<CrewRole>(it) },
    inviteCode = inviteCode,
    inviteCodeExpiresAt = inviteCodeExpiresAt?.let(ServerTime::parse),
    lastWeekDistance = Distance(lastWeekDistanceMeters)
)

internal fun MyCrewResponse.toDomain(): MyCrew = MyCrew(
    crew = crew?.toDomain(),
    pendingRequests = pendingRequests.map {
        MyCrew.PendingJoin(it.requestId, it.crewId, it.crewName, ServerTime.parse(it.requestedAt))
    }
)

/** 모르는 상태 이름은 PENDING으로 읽지 않고 실패(Server)로 본다: 가입했는지 모르는 채 화면을 고르지 않는다. */
internal fun JoinResponse.toDomain(): JoinResult = JoinResult(
    status = requireNotNull(enumByName<JoinResult.Status>(status)) { "모르는 가입 결과 $status" },
    crewId = crewId,
    requestId = requestId
)

internal fun CrewListResponse.toDomain(): CrewList = CrewList(
    nextCursor = nextCursor,
    crews = crews.map {
        CrewList.Item(
            crewId = it.crewId,
            name = it.name,
            intro = it.intro,
            tags = it.tags,
            leaderName = it.leaderName.orEmpty(),
            headcount = it.headcount,
            weeklyDistance = Distance(it.weeklyDistanceMeters),
            joinPolicy = enumByName<JoinPolicy>(it.joinPolicy) ?: JoinPolicy.APPROVAL
        )
    }
)

internal fun CrewMembersResponse.toDomain(): CrewMembers = CrewMembers(
    members.map {
        CrewMembers.Member(
            it.memberId,
            it.displayName.orEmpty(),
            enumByName<CrewRole>(it.role) ?: CrewRole.MEMBER,
            it.crewIntro,
            it.tier,
            it.runimalType,
            isMe = it.me
        )
    }
)

internal fun PendingRequestsResponse.toDomain(): PendingRequests = PendingRequests(
    requests.map {
        PendingRequests.Request(it.requestId, it.memberId, it.displayName.orEmpty(), ServerTime.parse(it.requestedAt))
    }
)

internal fun CrewRankingResponse.toDomain(): CrewRanking =
    CrewRanking(entries.map { CrewRanking.Entry(it.rank, it.displayName.orEmpty(), Distance(it.distanceMeters)) })

internal fun RaceStatusResponse.toDomain(): RaceStatus = RaceStatus(
    total = Distance(totalMeters),
    closesAt = ServerTime.parse(closesAt),
    contributors = contributors.map {
        RaceStatus.Contributor(it.displayName.orEmpty(), Distance(it.distanceMeters), it.share, it.memberId, it.me)
    },
    myShare = myShare,
    goal = goalMeters?.let(::Distance),
    goalProgressPercent = goalProgressPercent,
    achievedAt = achievedAt?.let(ServerTime::parse),
    celebration = celebration?.let {
        CelebrationCard(it.crewName, Distance(it.totalMeters), Distance(it.goalMeters), it.participantCount)
    }
)

/** 모르는 종류의 글은 목록에서 버린다(F.2.4). 투표 방식을 모르면 투표 요약 없이 둔다. */
internal fun PostListResponse.toDomain(): PostList = PostList(
    nextCursor = nextCursor,
    posts = posts.mapNotNull { post ->
        enumByName<PostList.Kind>(post.kind)?.let { kind ->
            PostList.Item(
                postId = post.postId,
                kind = kind,
                authorName = post.authorName.orEmpty(),
                summary = post.body.orEmpty(),
                writtenAt = ServerTime.parse(post.writtenAt),
                isMine = post.mine,
                crewRun = post.crewRun?.toDomain(),
                canDelete = post.canDelete,
                meetup = post.meetup?.toDomain(),
                notice = post.notice?.let { Notice(it.pinned) },
                poll = post.poll?.toDomain(),
                board =
                    if (kind == PostList.Kind.BOARD) {
                        BoardPost(
                            post.body.orEmpty(),
                            post.photos.orEmpty().map {
                                BoardPost.Photo(it.photoId, it.url)
                            }
                        )
                    } else {
                        null
                    }
            )
        }
    }
)

private fun PostListResponse.Item.Meetup.toDomain(): Meetup = Meetup(
    scheduledAt = ServerTime.parse(scheduledAt),
    placeName = placeName,
    capacity = capacity,
    goingCount = goingCount,
    notGoingCount = notGoingCount,
    undecidedCount = undecidedCount,
    myAnswer = myAnswer?.let { enumByName<AttendanceAnswer>(it) }
)

private fun PostListResponse.Item.Poll.toDomain(): Poll? = enumByName<PollMethod>(method)?.let {
    Poll(it, ServerTime.parse(closesAt), closed, responseCount, myOptionNo)
}

private fun PostListResponse.Item.CrewRun.toDomain(): PostList.CrewRunInfo = PostList.CrewRunInfo(
    distance = Distance(distanceMeters),
    scheduledAt = ServerTime.parse(scheduledAt),
    voteDeadline = voteDeadline?.let(ServerTime::parse),
    status = status?.let { enumByName<CrewRunStatus>(it) },
    goingCount = goingCount,
    myAnswer = myAnswer?.let { enumByName<AttendanceAnswer>(it) },
    finisherCount = finisherCount,
    raceContribution = raceContributionMeters?.let(::Distance),
    notGoingCount = notGoingCount,
    undecidedCount = undecidedCount
)

internal fun LobbyResponse.toDomain(): Lobby = Lobby(
    postId,
    participants.map {
        Lobby.Participant(
            it.displayName.orEmpty(),
            it.ready,
            it.memberId,
            it.me,
            isEntered = it.readiness?.let { r -> r != NOT_ENTERED } ?: true,
            faceKey = it.faceKey,
            shownGender = enumByName<Gender>(it.shownGender)
        )
    },
    ServerTime.parse(scheduledAt),
    Distance(distanceMeters)
)

internal fun CrewRunResultResponse.toDomain(): CrewRunResult {
    val people = participants.map {
        CrewRunResult.Participant(
            displayName = it.displayName.orEmpty(),
            distance = Distance(it.distanceMeters),
            elapsedSeconds = it.elapsedSeconds,
            averagePaceSecondsPerKm = it.averagePaceSecondsPerKm,
            hasEnded = it.ended,
            memberId = it.memberId,
            isMe = it.me,
            faceKey = it.faceKey,
            shownGender = enumByName<Gender>(it.shownGender)
        )
    }
    return CrewRunResult(
        postId = postId,
        participants = people,
        totalDistance = Distance(totalDistanceMeters ?: participants.sumOf { it.distanceMeters }),
        isFirstCrewRun = firstCrewRun,
        raceContribution = Distance(raceContributionMeters),
        status = status?.let { enumByName<CrewRunStatus>(it) },
        title = title.orEmpty(),
        distance = distanceMeters?.let(::Distance),
        startedAt = startedAt?.let(ServerTime::parse),
        myRecordId = myRecordId
    )
}

private const val NEWLINE = "\n"

/** 서버 crew.Readiness의 대기실에 들어오지 않은 값. */
private const val NOT_ENTERED = "NOT_ENTERED"

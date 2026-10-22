package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
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
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer

// 도메인 ↔ DTO 매퍼(13.2 toRequest·toDomain). 앱이 모르는 글 종류는 건너뛰고, 모르는 가입 방식은 승인제로 읽는다.

internal fun CrewForm.toRequest(): CrewFormDto = CrewFormDto(name, intro, tags, visibility.name, joinPolicy.name)

internal fun CrewRunForm.toRequest(): PostWriteRequest = PostWriteRequest(
    kind = PostList.Kind.CREW_RUN.name,
    body = listOfNotNull(title.takeIf { it.isNotBlank() }, body?.takeIf { it.isNotBlank() }).joinToString(NEWLINE),
    distanceMeters = distance.meters,
    meetAt = ServerTime.format(meetAt)
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
    inviteCodeExpiresAt = inviteCodeExpiresAt?.let(ServerTime::parse)
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

/** 서버가 보내지 않은 크루장·주간 거리·소개는 비워 둔다(추천 목록은 크루 이름·태그·정원·가입 방식만 준다). */
internal fun RecommendationsResponse.toDomain(): CrewList = CrewList(
    crews.map {
        CrewList.Item(
            crewId = it.crewId,
            name = it.name,
            intro = null,
            tags = it.tags,
            leaderName = "",
            headcount = it.headcount,
            weeklyDistance = Distance(0),
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
    myShare = myShare
)

internal fun PostListResponse.toDomain(): PostList = PostList(
    nextCursor = nextCursor,
    posts = posts.mapNotNull { post ->
        enumByName<PostList.Kind>(post.kind)?.let { kind ->
            PostList.Item(
                post.postId,
                kind,
                post.authorName.orEmpty(),
                post.body.orEmpty(),
                ServerTime.parse(post.writtenAt),
                post.mine,
                post.crewRun?.toDomain()
            )
        }
    }
)

private fun PostListResponse.Item.CrewRun.toDomain(): PostList.CrewRunInfo = PostList.CrewRunInfo(
    distance = Distance(distanceMeters),
    scheduledAt = ServerTime.parse(scheduledAt),
    voteDeadline = voteDeadline?.let(ServerTime::parse),
    status = status?.let { enumByName<CrewRunStatus>(it) },
    goingCount = goingCount,
    myAnswer = myAnswer?.let { enumByName<VoteAnswer>(it) },
    finisherCount = finisherCount,
    raceContribution = raceContributionMeters?.let(::Distance)
)

internal fun LobbyResponse.toDomain(): Lobby = Lobby(
    postId,
    participants.map { Lobby.Participant(it.displayName.orEmpty(), it.ready, it.memberId, it.me) },
    ServerTime.parse(scheduledAt),
    Distance(distanceMeters)
)

internal fun CrewRunResultResponse.toDomain(): CrewRunResult = CrewRunResult(
    postId = postId,
    finishers =
        finishers.map {
            CrewRunResult.Finisher(
                it.rank,
                it.displayName.orEmpty(),
                it.elapsedSeconds,
                it.averagePaceSecondsPerKm,
                hasQuit = it.quit,
                memberId = it.memberId,
                isMe = it.me
            )
        },
    isFirstCrewRun = firstCrewRun,
    raceContribution = Distance(raceContributionMeters)
)

private const val NEWLINE = "\n"

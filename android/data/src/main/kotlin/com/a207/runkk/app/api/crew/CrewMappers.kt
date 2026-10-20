package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus

// 도메인 ↔ DTO 매퍼(13.2 toRequest·toDomain). 앱이 모르는 글 종류는 건너뛰고, 모르는 가입 방식은 승인제로 읽는다.

internal fun CrewForm.toRequest(): CrewFormDto = CrewFormDto(name, intro, tags, visibility.name, joinPolicy.name)

internal fun CrewRunForm.toRequest(): PostWriteRequest = PostWriteRequest(
    kind = PostList.Kind.CREW_RUN.name,
    title = title,
    body = body,
    distanceMeters = distance.meters,
    meetAt = ServerTime.format(meetAt)
)

internal fun CrewListResponse.toDomain(): CrewList = CrewList(
    crews.map {
        CrewList.Item(
            crewId = it.crewId,
            name = it.name,
            intro = it.intro,
            tags = it.tags,
            leaderName = it.leaderName,
            headcount = it.headcount,
            weeklyDistance = Distance(it.weeklyDistanceMeters),
            joinPolicy = enumByName<JoinPolicy>(it.joinPolicy) ?: JoinPolicy.APPROVAL
        )
    }
)

internal fun CrewMembersResponse.toDomain(): CrewMembers = CrewMembers(
    members.map { CrewMembers.Member(it.memberId, it.displayName, it.role, it.crewIntro, it.tier, it.runimalType) }
)

internal fun PendingRequestsResponse.toDomain(): PendingRequests = PendingRequests(
    requests.map {
        PendingRequests.Request(it.requestId, it.memberId, it.displayName, ServerTime.parse(it.requestedAt))
    }
)

internal fun CrewRankingResponse.toDomain(): CrewRanking =
    CrewRanking(entries.map { CrewRanking.Entry(it.rank, it.displayName, Distance(it.distanceMeters)) })

internal fun RaceStatusResponse.toDomain(): RaceStatus = RaceStatus(
    total = Distance(totalMeters),
    closesAt = ServerTime.parse(closesAt),
    contributors = contributors.map { RaceStatus.Contributor(it.displayName, Distance(it.distanceMeters), it.share) },
    myShare = myShare
)

internal fun PostListResponse.toDomain(): PostList = PostList(
    posts.mapNotNull { post ->
        enumByName<PostList.Kind>(post.kind)?.let { kind ->
            PostList.Item(post.postId, kind, post.authorName, post.summary, ServerTime.parse(post.writtenAt), post.mine)
        }
    }
)

internal fun LobbyResponse.toDomain(): Lobby =
    Lobby(postId, participants.map { Lobby.Participant(it.displayName, it.ready) }, ServerTime.parse(meetAt))

internal fun CrewRunResultResponse.toDomain(): CrewRunResult = CrewRunResult(
    postId = postId,
    finishers =
    finishers.map {
        CrewRunResult.Finisher(it.rank, it.displayName, it.elapsedSeconds, it.averagePaceSecondsPerKm)
    },
    firstCrewRun = firstCrewRun,
    raceContribution = Distance(raceContributionMeters)
)

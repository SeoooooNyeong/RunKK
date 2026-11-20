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
import com.a207.runkk.app.domain.crew.CrewPreview
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunEdit
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunJoin
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.LobbyPhase
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.MyRunState
import com.a207.runkk.app.domain.crew.Notice
import com.a207.runkk.app.domain.crew.PastCrewRun
import com.a207.runkk.app.domain.crew.PastCrewRuns
import com.a207.runkk.app.domain.crew.PastGoals
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.Poll
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.RecentCrewRun
import java.time.DayOfWeek
import java.time.YearMonth

// 도메인 ↔ DTO 매퍼(13.2 toRequest·toDomain). 앱이 모르는 글 종류는 건너뛰고, 모르는 가입 방식은 승인제로 읽는다.

internal fun CrewForm.toRequest(): CrewFormDto =
    CrewFormDto(name, intro, tags, visibility.name, joinPolicy.name, logoUrl)

/** 제목과 내용을 따로 싣는다(W6). 거리가 null이면 제한 없음, 모집 일시는 오프셋 시각이다. */
internal fun CrewRunForm.toRequest(): PostWriteRequest = PostWriteRequest(
    kind = PostList.Kind.CREW_RUN.name,
    title = title,
    body = body.orEmpty(),
    distanceMeters = distance?.meters,
    scheduledAt = ServerTime.formatWithOffset(meetAt),
    repeat = repeat.name
)

internal fun CrewRunEdit.toRequest(): CrewRunEditRequest =
    CrewRunEditRequest(title = title, body = body.orEmpty(), distanceMeters = distance?.meters, repeat = repeat.name)

/** 모르는 상태와 요일은 null, 모르는 반복은 반복 안 함으로 읽는다. */
internal fun CrewRunDetailResponse.toDomain(): CrewRunDetail = CrewRunDetail(
    postId = postId,
    crewId = crewId,
    title = title,
    body = body,
    authorName = authorName.orEmpty(),
    isMine = mine,
    writtenAt = ServerTime.parse(writtenAt),
    scheduledAt = ServerTime.parse(scheduledAt),
    distance = distanceMeters?.let(::Distance),
    status = status?.let { enumByName<CrewRunStatus>(it) },
    goingCount = goingCount,
    myAnswer = myAnswer?.let { enumByName<AttendanceAnswer>(it) },
    canEdit = canEdit,
    canDelete = canDelete,
    repeat = repeat?.let { enumByName<CrewRunRepeat>(it) } ?: CrewRunRepeat.NONE,
    repeatDay = repeatDayOfWeek?.let { enumByName<DayOfWeek>(it) },
    attendees = attendees.map {
        CrewRunDetail.Attendee(
            memberId = it.memberId,
            displayName = it.displayName.orEmpty(),
            isAuthor = it.author,
            isMe = it.me,
            faceKey = it.faceKey,
            shownGender = enumByName<Gender>(it.shownGender),
            distance = it.distanceMeters?.let(::Distance)
        )
    },
    myRun = myRunOf(myRun)
)

/** 열람자의 참여 상태. 옛 서버가 빼거나 모르는 값이면 NONE이다. */
private fun myRunOf(name: String?): MyRunState = enumByName<MyRunState>(name) ?: MyRunState.NONE

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
    lastWeekDistance = Distance(lastWeekDistanceMeters),
    createdAt = createdAt?.let(ServerTime::parse),
    totalDistance = Distance(totalDistanceMeters),
    crewRunCount = crewRunCount,
    recentCrewRuns = recentCrewRuns.map {
        RecentCrewRun(
            it.postId,
            ServerTime.parse(it.scheduledAt),
            it.participantCount,
            Distance(it.totalDistanceMeters)
        )
    },
    joinableAt = rejoinAvailableAt?.let(ServerTime::parse)
)

/** 초대 코드 미리보기. 모르는 공개 범위는 비공개로(상세 이동을 숨긴다), 가입 방식은 승인제로 읽는다. */
internal fun InvitePreviewResponse.toDomain(): CrewPreview = CrewPreview(
    crewId = crewId,
    name = name,
    intro = intro,
    tags = tags,
    logoUrl = logoUrl,
    visibility = enumByName<CrewVisibility>(visibility) ?: CrewVisibility.PRIVATE,
    joinPolicy = enumByName<JoinPolicy>(joinPolicy) ?: JoinPolicy.APPROVAL,
    headcount = headcount,
    createdAt = ServerTime.parse(createdAt)
)

internal fun MyCrewResponse.toDomain(): MyCrew = MyCrew(
    crew = crew?.toDomain(),
    pendingRequests = pendingRequests.map {
        MyCrew.PendingJoin(it.requestId, it.crewId, it.crewName, ServerTime.parse(it.requestedAt))
    },
    joinableAt = rejoinAvailableAt?.let(ServerTime::parse)
)

/** 모르는 상태 이름은 PENDING으로 읽지 않고 실패(Server)로 본다: 가입했는지 모르는 채 화면을 고르지 않는다. */
internal fun JoinResponse.toDomain(): JoinResult = JoinResult(
    status = requireNotNull(enumByName<JoinResult.Status>(status)) { "모르는 가입 결과 $status" },
    crewId = crewId,
    requestId = requestId
)

internal fun CrewListResponse.toDomain(): CrewList = CrewList(
    nextCursor = nextCursor,
    totalCount = totalCount,
    crews = crews.map {
        CrewList.Item(
            crewId = it.crewId,
            name = it.name,
            intro = it.intro,
            tags = it.tags,
            leaderName = it.leaderName.orEmpty(),
            headcount = it.headcount,
            weeklyDistance = Distance(it.weeklyDistanceMeters),
            joinPolicy = enumByName<JoinPolicy>(it.joinPolicy) ?: JoinPolicy.APPROVAL,
            logoUrl = it.logoUrl
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
            isMe = it.me,
            accumulated = Distance(it.accumulatedMeters),
            joinedAt = it.joinedAt?.let { at -> runCatching { ServerTime.parse(at) }.getOrNull() },
            level = it.level,
            ageBand = it.ageBand,
            gender = it.gender?.let { g -> enumByName<Gender>(g) },
            isAgePublic = it.ageBandVisibility?.let { v -> v == VISIBILITY_PUBLIC },
            isGenderPublic = it.genderVisibility?.let { v -> v == VISIBILITY_PUBLIC },
            faceKey = it.faceKey,
            shownGender = enumByName<Gender>(it.shownGender)
        )
    }
)

internal fun PendingRequestsResponse.toDomain(): PendingRequests = PendingRequests(
    requests.map {
        PendingRequests.Request(
            requestId = it.requestId,
            memberId = it.memberId,
            displayName = it.displayName.orEmpty(),
            requestedAt = ServerTime.parse(it.requestedAt),
            tier = it.tier,
            level = it.level,
            ageBand = it.ageBand,
            gender = it.gender?.let { g -> enumByName<Gender>(g) }
        )
    }
)

/** 지난 공동 목표 한 쪽(contract-W7 4장). 날짜를 읽지 못한 주는 버린다. */
internal fun GoalHistoryResponse.toDomain(): PastGoals = PastGoals(
    goals = goals.mapNotNull { g ->
        runCatching { ServerTime.parseDate(g.weekStart) }.getOrNull()?.let { week ->
            PastGoals.Goal(
                weekStart = week,
                month = g.month,
                weekOfMonth = g.weekOfMonth,
                goal = Distance(g.goalMeters),
                total = Distance(g.totalMeters),
                isAchieved = g.achieved,
                progressPercent = g.progressPercent,
                participantCount = g.participantCount
            )
        }
    },
    nextBefore = nextBefore?.let { runCatching { ServerTime.parseDate(it) }.getOrNull() }
)

/** 지난 크루런 한 달(contract-W6). 날짜·시각을 읽지 못한 항목은 버린다. */
internal fun PastCrewRunsResponse.toDomain(requested: YearMonth): PastCrewRuns = PastCrewRuns(
    month = runCatching { YearMonth.parse(month) }.getOrDefault(requested),
    days = dates.mapNotNull { runCatching { ServerTime.parseDate(it) }.getOrNull() }.toSet(),
    runs = runs.mapNotNull { r ->
        runCatching { ServerTime.parse(r.scheduledAt) }.getOrNull()?.let { at ->
            PastCrewRun(
                postId = r.postId,
                title = r.title,
                scheduledAt = at,
                distance = r.distanceMeters?.let(::Distance),
                status = enumByName<CrewRunStatus>(r.status) ?: CrewRunStatus.FINISHED,
                goingCount = r.goingCount,
                runnerCount = r.runnerCount
            )
        }
    },
    earliestMonth = earliestMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
)

private const val VISIBILITY_PUBLIC = "PUBLIC"

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
    distance = distanceMeters?.let(::Distance),
    scheduledAt = ServerTime.parse(scheduledAt),
    voteDeadline = voteDeadline?.let(ServerTime::parse),
    status = status?.let { enumByName<CrewRunStatus>(it) },
    goingCount = goingCount,
    myAnswer = myAnswer?.let { enumByName<AttendanceAnswer>(it) },
    finisherCount = finisherCount,
    raceContribution = raceContributionMeters?.let(::Distance),
    notGoingCount = notGoingCount,
    undecidedCount = undecidedCount,
    title = title.orEmpty(),
    myRun = myRunOf(myRun)
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
            shownGender = enumByName<Gender>(it.shownGender),
            look = it.look?.toDomain()
        )
    },
    ServerTime.parse(scheduledAt),
    distanceMeters?.let(::Distance),
    crewId = crewId,
    revision = revision,
    phase = enumByName<LobbyPhase>(phase),
    countdownToken = countdownToken,
    startAt = startAt?.let(ServerTime::parse),
    countdownParticipantIds = countdownParticipantIds,
    canStartNow = canStartNow,
    canJoin = canJoin,
    isMeRunner = meRunner,
    measurementRecordId = measurementRecordId
)

/** 중간 참여 승인. 내 줄(me)이 끝났으면 isEnded다. */
internal fun ParticipationResponse.toDomain(): CrewRunJoin = CrewRunJoin(
    postId = postId,
    crewId = crewId,
    revision = revision,
    joinedAt = ServerTime.parse(joinedAt),
    measurementRecordId = measurementRecordId,
    isAlreadyJoined = alreadyJoined,
    isEnded = participants.firstOrNull { it.me }?.ended == true
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

/** 서버 crew.Readiness의 대기실에 들어오지 않은 값. */
private const val NOT_ENTERED = "NOT_ENTERED"

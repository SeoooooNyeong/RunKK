package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.Album
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRunForm
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.JoinResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.MeetupForm
import com.a207.runkk.app.domain.crew.MyCrew
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PollForm
import com.a207.runkk.app.domain.crew.PollMethod
import com.a207.runkk.app.domain.crew.PollResult
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 크루·게시판·대기실 포트를 REST로 구현한다(6.1.14 api.crew). 갈래마다 엔드포인트가 나뉜다(D-36).
 * 목록(크루·게시글)은 [PAGE_SIZE]씩, 앨범은 [ALBUM_PAGE_SIZE]씩 받아 커서로 이어 받는다. 게시판 사진은 PhotoCompressor가 줄여 보낸다.
 */
internal class CrewRepositoryImpl(
    private val api: CrewApi,
    private val photos: PhotoCompressor,
    /** 사진 압축·사진 바이트 읽기 디스패처(리뷰 N-10·AN5-C-10: 하드코딩하지 않고 받는다, 시험이 바꿔 끼운다). */
    private val ioDispatcher: CoroutineDispatcher
) : CrewRepository {
    @Inject
    constructor(api: CrewApi, photos: PhotoCompressor) : this(api, photos, Dispatchers.IO)

    override suspend fun myCrew(): Outcome<MyCrew> = ApiErrorMapper.call { api.myCrew().toDomain() }

    override suspend fun detail(crewId: Long): Outcome<CrewDetail> = ApiErrorMapper.call {
        api.detail(crewId).toDomain()
    }

    override suspend fun create(form: CrewForm): Outcome<Long> = ApiErrorMapper.call { api.create(form.toRequest()).id }

    override suspend fun changeInfo(crewId: Long, form: CrewForm): Outcome<Unit> =
        ApiErrorMapper.call { api.changeInfo(crewId, form.toRequest()) }

    override suspend fun reissueInviteCode(crewId: Long): Outcome<String> =
        ApiErrorMapper.call { api.reissueInviteCode(crewId).inviteCode }

    override suspend fun transferLeadership(crewId: Long, memberId: Long): Outcome<Unit> =
        ApiErrorMapper.call { api.transferLeadership(crewId, LeaderRequest(memberId)) }

    override suspend fun delete(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.delete(crewId) }

    override suspend fun search(keyword: String?, tags: Set<String>, cursor: Long?): Outcome<CrewList> =
        ApiErrorMapper.call { api.search(keyword, tags.sorted(), cursor, PAGE_SIZE).toDomain() }

    override suspend fun apply(crewId: Long): Outcome<JoinResult> = ApiErrorMapper.call {
        api.apply(JoinRequestBody(crewId = crewId)).toDomain()
    }

    override suspend fun applyWithInviteCode(code: String): Outcome<JoinResult> =
        ApiErrorMapper.call { api.apply(JoinRequestBody(inviteCode = code)).toDomain() }

    override suspend fun cancelJoinRequest(requestId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.cancelJoinRequest(requestId)
        Unit
    }

    override suspend fun approveJoin(requestId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.approveJoin(requestId)
    }

    override suspend fun rejectJoin(requestId: Long): Outcome<Unit> = ApiErrorMapper.call { api.rejectJoin(requestId) }

    override suspend fun leave(crewId: Long): Outcome<Unit> = ApiErrorMapper.call { api.leave(crewId) }

    override suspend fun kick(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.kick(crewId, memberId)
    }

    override suspend fun editCrewProfile(crewId: Long, intro: String): Outcome<Unit> =
        ApiErrorMapper.call { api.editCrewProfile(crewId, CrewProfileRequest(intro)) }

    override suspend fun members(crewId: Long): Outcome<CrewMembers> = ApiErrorMapper.call {
        api.members(crewId).toDomain()
    }

    override suspend fun pendingRequests(crewId: Long): Outcome<PendingRequests> =
        ApiErrorMapper.call { api.pendingRequests(crewId).toDomain() }

    override suspend fun crewRanking(crewId: Long, period: CrewRanking.Period): Outcome<CrewRanking> =
        ApiErrorMapper.call { api.crewRanking(crewId, period.name).toDomain() }

    override suspend fun race(crewId: Long, weekStart: LocalDate?): Outcome<RaceStatus> =
        ApiErrorMapper.call { api.race(crewId, weekStart?.let(ServerTime::formatDate)).toDomain() }

    override suspend fun posts(crewId: Long, kind: PostList.Kind?, cursor: Long?): Outcome<PostList> =
        ApiErrorMapper.call { api.posts(crewId, kind?.name, cursor, PAGE_SIZE).toDomain() }

    override suspend fun writeCheer(crewId: Long, body: String): Outcome<Long> =
        ApiErrorMapper.call { api.writePost(crewId, PostWriteRequest(kind = PostList.Kind.CHEER.name, body = body)).id }

    override suspend fun writeCrewRun(crewId: Long, form: CrewRunForm): Outcome<Long> =
        ApiErrorMapper.call { api.writePost(crewId, form.toRequest()).id }

    override suspend fun deletePost(postId: Long): Outcome<Unit> = ApiErrorMapper.call { api.deletePost(postId) }

    /** 일정(크루런·대면 모임) 참석 응답(PUT /posts/{id}/attendance, D-99). */
    override suspend fun answerAttendance(postId: Long, answer: AttendanceAnswer): Outcome<Unit> = ApiErrorMapper.call {
        api.answerAttendance(postId, AttendanceRequest(answer.name))
        Unit
    }

    override suspend fun appointManager(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.appointManager(crewId, memberId)
        Unit
    }

    override suspend fun dismissManager(crewId: Long, memberId: Long): Outcome<Unit> = ApiErrorMapper.call {
        api.dismissManager(crewId, memberId)
        Unit
    }

    override suspend fun writeNotice(crewId: Long, body: String, isPinned: Boolean): Outcome<Long> =
        ApiErrorMapper.call {
            api.writePost(crewId, PostWriteRequest(kind = PostList.Kind.NOTICE.name, body = body, pinned = isPinned)).id
        }

    override suspend fun pin(postId: Long, isPinned: Boolean): Outcome<Unit> = ApiErrorMapper.call {
        api.pin(postId, PinRequest(isPinned))
        Unit
    }

    /** YES_NO면 선택지를 보내지 않는다(서버가 찬성·반대를 만든다, F.2.2). */
    override suspend fun writePoll(crewId: Long, form: PollForm): Outcome<Long> = ApiErrorMapper.call {
        val request =
            PostWriteRequest(
                kind = PostList.Kind.POLL.name,
                body = form.question,
                pollMethod = form.method.name,
                options = form.options.takeIf { form.method == PollMethod.CHOICES },
                closesAt = ServerTime.formatWithOffset(form.closesAt)
            )
        api.writePost(crewId, request).id
    }

    override suspend fun respondPoll(postId: Long, optionNo: Int): Outcome<Unit> = ApiErrorMapper.call {
        api.respondPoll(postId, PollAnswerRequest(optionNo))
        Unit
    }

    override suspend fun pollResult(postId: Long): Outcome<PollResult> =
        ApiErrorMapper.call { api.pollResult(postId).toDomain() }

    override suspend fun writeMeetup(crewId: Long, form: MeetupForm): Outcome<Long> = ApiErrorMapper.call {
        val request =
            PostWriteRequest(
                kind = PostList.Kind.MEETUP.name,
                body = form.body,
                scheduledAt = ServerTime.formatWithOffset(form.scheduledAt),
                placeName = form.placeName,
                capacity = form.capacity
            )
        api.writePost(crewId, request).id
    }

    /**
     * 게시판 글(POST /crews/{id}/board-posts, multipart, FR-211). 사진은 PhotoCompressor로 장마다 1MB 이하 JPEG로 줄인다.
     * 한 장이라도 줄이지 못하면(원본 10MB 초과 TOO_LARGE, JPEG·PNG 아님 UNSUPPORTED_IMAGE) 보내지 않고 그 거부를 돌려준다.
     */
    override suspend fun writeBoard(crewId: Long, body: String, photoUris: List<String>): Outcome<Long> {
        val compressed = photoUris.map { uri -> withContext(ioDispatcher) { photos.compress(uri) } }
        compressed.filterIsInstance<Outcome.Failure>().firstOrNull()?.let { return it }
        val parts =
            compressed.filterIsInstance<Outcome.Success<ByteArray>>().map { photo ->
                MultipartBody.Part.createFormData(PHOTOS_PART, PHOTO_FILE, photo.value.toRequestBody(JPEG))
            }
        return ApiErrorMapper.call { api.writeBoard(crewId, body.toRequestBody(TEXT), parts).id }
    }

    /** 사진 한 장(FR-212). [MAX_PHOTO_BYTES]를 넘는 응답은 끝까지 읽지 않고 TOO_LARGE로 거부한다(앱이 올린 사진은 1MB 이하, D-100). */
    override suspend fun photo(photoId: Long): Outcome<ByteArray> {
        val read = ApiErrorMapper.call {
            api.photo(photoId).use { body ->
                withContext(ioDispatcher) {
                    body.byteStream().use { stream -> stream.readAtMost(MAX_PHOTO_BYTES + 1) }
                }
            }
        }
        return if (read is Outcome.Success && read.value.size > MAX_PHOTO_BYTES) {
            Outcome.Failure(AppError.Rejected(TOO_LARGE))
        } else {
            read
        }
    }

    override suspend fun album(crewId: Long, cursor: Long?): Outcome<Album> =
        ApiErrorMapper.call { api.album(crewId, cursor, ALBUM_PAGE_SIZE).toDomain() }

    override suspend fun setWeeklyGoal(crewId: Long, goal: Distance): Outcome<RaceStatus> =
        ApiErrorMapper.call { api.setGoal(crewId, GoalRequest(goal.meters)).toDomain() }

    override suspend fun enterLobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.enterLobby(postId).toDomain()
    }

    override suspend fun toggleReady(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.toggleReady(postId).toDomain()
    }

    override suspend fun leaveLobby(postId: Long): Outcome<Lobby> = ApiErrorMapper.call {
        api.leaveLobby(postId).toDomain()
    }

    override suspend fun quit(postId: Long, distanceMeters: Long?, elapsed: Duration?): Outcome<Unit> =
        ApiErrorMapper.call { api.quit(postId, CrewRunQuitRequest(distanceMeters, elapsed?.seconds)) }

    override suspend fun pause(postId: Long, elapsed: Duration): Outcome<Unit> =
        ApiErrorMapper.call { api.pause(postId, CrewRunPauseRequest(elapsed.seconds)) }

    override suspend fun crewRunResult(postId: Long): Outcome<CrewRunResult> =
        ApiErrorMapper.call { api.result(postId).toDomain() }

    private companion object {
        /** 크루 목록·게시글 한 쪽 크기(서버 PageCursor 1~100). */
        const val PAGE_SIZE = 20

        /** 앨범 한 쪽 크기(서버 기본 30). */
        const val ALBUM_PAGE_SIZE = 30
        const val PHOTOS_PART = "photos"

        /** 받는 사진 한 장의 상한(올린 사진 1MB 이하의 두 배). */
        const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
        const val TOO_LARGE = "TOO_LARGE"
        const val PHOTO_FILE = "photo.jpg"
        val JPEG = "image/jpeg".toMediaType()
        val TEXT = "text/plain".toMediaType()
    }
}

/** 최대 [limit]바이트까지 읽는다(InputStream.readNBytes는 API 33부터라 직접 센다). */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private const val BUFFER_BYTES = 8 * 1024

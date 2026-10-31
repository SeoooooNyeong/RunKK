package com.a207.runkk.watch.cache

import android.content.Context
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 셀프·셀럽 상대 기록을 워치에 두고 꺼내 준다(3.4, FR-260, D-377). 연결이 없을 때 오프라인 기록 대결(OfflineRecordBattle)의 상대가 된다.
 *
 * - [refresh]: GET /battles/record-tracks?kind=SELF·CELEBRITY로 받아 파일(JSON 한 개, 회원ID 머리)에 둔다. 받기가 실패한 종류는 이전 것을
 *   그대로 둔다. 회원ID는 부르는 쪽이 넘긴다: 이 패키지는 watch.session을 모른다(M78). 파일의 회원ID가 다르면 이전 기록을 버린다.
 * - 꺼내기([selfCandidates]·[celebrityCandidates])는 메모리 값이다. 아직 파일을 읽지 않았으면 그 자리에서 한 번 읽는다.
 * - [onNewSelfBest]: 워치 셀프런의 새 최고 기록을 다음 받기 전까지 그 거리의 상대로 쓴다(FR-260 제안). 파일 쓰기는 앱 범위에서 한다.
 * - [clear]: 로그아웃·다른 회원 로그인 때 watch.session이 부른다(FR-241, H-56).
 */
@Singleton
class OpponentRecordCache internal constructor(
    private val file: File,
    private val battleRepository: BattleRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        battleRepository: BattleRepository,
        clock: Clock,
        scope: CoroutineScope
    ) : this(File(context.filesDir, FILE_NAME), battleRepository, clock, scope, Dispatchers.IO)

    private val lock = Any()

    /** 메모리 사본. null이면 아직 파일을 읽지 않았다. lock 안에서만 쓴다. */
    private var cached: Snapshot? = null

    /**
     * 지운 세대. [clear]마다 1씩 는다. 지우기 전에 시작한 받기·쓰기는 끝날 때 세대가 다르면 메모리·파일에 남기지 않는다
     * (로그아웃한 회원의 기록이 되살아나지 않게, H-56). lock 안에서만 쓴다.
     */
    private var generation = 0L

    /** 파일 읽기·쓰기를 한 줄로 세운다. */
    private val fileMutex = Mutex()

    /** 회원ID와 그 회원의 상대 기록. memberId가 null이면 비어 있다. */
    private data class Snapshot(val memberId: Long?, val tracks: List<CachedTrack>) {
        companion object {
            val EMPTY = Snapshot(null, emptyList())
        }
    }

    suspend fun refresh(memberId: Long): Outcome<Unit> {
        val startedIn = withContext(ioDispatcher) {
            fileMutex.withLock {
                synchronized(lock) {
                    snapshotLocked()
                    generation
                }
            }
        }
        val fetched = RecordBattleKind.entries.associateWith { battleRepository.recordTracks(it) }
        val fetchedAt = clock.instant()
        val isApplied = synchronized(lock) {
            if (generation != startedIn) return@synchronized false
            // 받는 동안 바뀐 값(onNewSelfBest)을 잃지 않게 지금 메모리 사본 위에 받은 종류만 바꾼다.
            var tracks = snapshotLocked().takeIf { it.memberId == memberId }?.tracks.orEmpty()
            fetched.forEach { (kind, outcome) ->
                if (outcome is Outcome.Success) {
                    tracks = tracks.filter { it.record.kind != kind } + outcome.value.map { CachedTrack(it, fetchedAt) }
                }
            }
            cached = Snapshot(memberId, tracks)
            true
        }
        if (isApplied) persist(startedIn) else Timber.i("받는 동안 상대 기록을 지워 받은 값을 버린다")
        return fetched.values.filterIsInstance<Outcome.Failure>().firstOrNull() ?: Outcome.Success(Unit)
    }

    fun selfCandidates(): List<CachedTrack> = snapshot().tracks.filter { it.record.kind == RecordBattleKind.SELF }

    fun celebrityCandidates(distance: Distance): List<CachedTrack> = snapshot().tracks.filter {
        it.record.kind == RecordBattleKind.CELEBRITY && it.record.distance == distance
    }

    /** 새 기록이 그 거리의 셀프 상대보다 빠를 때만 바꾼다. 기록이 없는 거리는 셀프런을 시작할 수 없으므로 더하지 않는다. */
    fun onNewSelfBest(distance: Distance, track: OpponentTrack) {
        val finish = track.arrivalTime(distance) ?: return
        val changedIn = synchronized(lock) {
            val current = snapshotLocked()
            var isChanged = false
            val tracks = current.tracks.map { cachedTrack ->
                val record = cachedTrack.record
                val isFaster = record.kind == RecordBattleKind.SELF && record.distance == distance &&
                    finish < record.finishTime
                if (isFaster) {
                    isChanged = true
                    cachedTrack.copy(record = record.copy(finishTime = finish, track = track))
                } else {
                    cachedTrack
                }
            }
            if (!isChanged) return
            cached = current.copy(tracks = tracks)
            generation
        }
        scope.launch(ioDispatcher) { persist(changedIn) }
    }

    suspend fun clear() {
        synchronized(lock) {
            generation++
            cached = Snapshot.EMPTY
        }
        withContext(ioDispatcher) {
            fileMutex.withLock {
                if (file.exists() && !file.delete()) Timber.w("상대 기록 파일을 지우지 못했다")
            }
        }
    }

    /** 지금 메모리 사본을 파일에 쓴다. [expected] 뒤에 지웠으면 쓰지 않는다. */
    private suspend fun persist(expected: Long) {
        withContext(ioDispatcher) {
            fileMutex.withLock {
                val latest = synchronized(lock) { cached.takeIf { generation == expected } } ?: return@withLock
                write(latest)
            }
        }
    }

    private fun snapshot(): Snapshot = synchronized(lock) { snapshotLocked() }

    /** 메모리 사본. 아직 없으면 파일을 읽는다(lock 안에서만 부른다). */
    private fun snapshotLocked(): Snapshot = cached ?: read().also { cached = it }

    private fun read(): Snapshot {
        if (!file.exists()) return Snapshot.EMPTY
        return try {
            val records = JSON.decodeFromString(CachedRecords.serializer(), file.readText())
            Snapshot(records.memberId, records.tracks.map { it.toCached() })
        } catch (e: IOException) {
            Timber.w(e, "상대 기록 파일을 읽지 못했다")
            Snapshot.EMPTY
        } catch (e: SerializationException) {
            Timber.w(e, "상대 기록 파일을 해독하지 못했다")
            Snapshot.EMPTY
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "상대 기록 파일 값이 맞지 않는다")
            Snapshot.EMPTY
        }
    }

    /** 임시 파일에 쓴 뒤 바꿔 넣는다(쓰는 도중 죽어도 이전 파일이 남는다). fileMutex 안에서 부른다. */
    private fun write(snapshot: Snapshot) {
        val memberId = snapshot.memberId ?: return
        val records = CachedRecords(memberId, snapshot.tracks.map { CachedRecords.Entry.of(it) })
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(JSON.encodeToString(CachedRecords.serializer(), records))
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) Timber.w("상대 기록 파일을 바꾸지 못했다")
            }
        } catch (e: IOException) {
            Timber.w(e, "상대 기록 파일을 쓰지 못했다")
        }
    }

    private companion object {
        const val FILE_NAME = "opponent-records.json"
        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

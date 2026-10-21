package com.a207.runkk.app.data.store

import androidx.room.withTransaction
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.UploadScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 미전송 기록을 Room에 보관한다(6.1.14 data.store, D-54, FR-039). 저장 오류는 AppError.Storage로 바꿔 돌려준다(5.1.2).
 *
 * 늦은 심박(FR-043)의 자리: 측정이 끝난 뒤 온 심박은 RunTracker가 appendHeartRates로 넘긴다.
 * - 아직 보내지 않은 기록이면 그 기록의 심박에 시각 순서로 덧붙인다(다음 전송에 함께 간다).
 * - 이미 보내 remove된 기록이면, remove가 하루 동안 남겨 둔 머리 정보(경로·심박 없음) + 늦은 심박으로 미전송 기록을 다시 만들고
 *   전송을 요청한다. 서버는 같은 기록ID를 "이미 저장됨"으로 받고(D-11), UploadPendingRunsUseCase가 심박만 덧붙여 보낸다.
 * - 머리 정보도 없으면(하루가 지났거나 탈퇴로 지움) Rejected(RECORD_NOT_FOUND).
 */
internal class RoomPendingRunRepository
@Inject
constructor(
    private val database: PendingRunDatabase,
    private val uploadScheduler: UploadScheduler,
    private val clock: Clock
) : PendingRunRepository {
    private val dao = database.pendingRunDao()

    /**
     * 마지막 pending()이 내준 기록별 심박 측정 시각. 전송(UploadPendingRunsUseCase)이 읽은 뒤 remove 전에 덧붙은 늦은 심박을
     * 가려내려고 기억한다(리뷰 F-4: remove가 그 심박까지 지우지 않게).
     */
    private val handedOutHeartRates = ConcurrentHashMap<String, Set<Long>>()

    override suspend fun save(run: CompletedRun): Outcome<Unit> = storage { dao.upsert(run.toEntity()) }

    override suspend fun pending(): Outcome<List<CompletedRun>> = storage {
        dao.pending().map { row ->
            row.toDomain().also { run ->
                handedOutHeartRates[run.recordId] = run.heartRates.map { it.at.toEpochMilli() }.toSet()
            }
        }
    }

    /** 읽기 오류는 빈 목록으로 내보내고 끝낸다(화면의 "전송 대기" 표시가 앱을 죽이지 않게). */
    override fun observePending(): Flow<List<CompletedRun>> = dao
        .observePending()
        .map { rows -> rows.map { it.toDomain() } }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }

    /**
     * 보낸 기록을 미전송 목록에서 뺀다. 경로·심박은 지우고 머리 정보만 하루 남긴다.
     * 전송이 읽은 뒤 덧붙은 늦은 심박이 있으면 그 심박만 담은 미전송 기록으로 남기고 전송을 요청한다(F-4).
     */
    override suspend fun remove(recordId: String): Outcome<Unit> {
        val leftLate =
            when (val removed = storage { removeInTransaction(recordId) }) {
                is Outcome.Failure -> return removed
                is Outcome.Success -> removed.value
            }
        if (leftLate) uploadScheduler.requestUpload()
        return Outcome.Success(Unit)
    }

    /** 늦은 심박이 남았으면 참. */
    private suspend fun removeInTransaction(recordId: String): Boolean {
        val now = clock.instant()
        val handedOut = handedOutHeartRates.remove(recordId)
        return database.withTransaction {
            val row = dao.find(recordId)?.takeIf { it.sentAtMillis == null }
            val late =
                row?.let { found ->
                    val sent = handedOut ?: decodeHeartRates(found.heartRates).map { it.at.toEpochMilli() }.toSet()
                    decodeHeartRates(found.heartRates).filter { it.at.toEpochMilli() !in sent }
                }.orEmpty()
            if (row != null) {
                val header = row.toSentHeader(now.toEpochMilli(), EMPTY_BLOB)
                dao.upsert(
                    if (late.isEmpty()) {
                        header
                    } else {
                        header.copy(
                            heartRates = encodeHeartRates(late),
                            sentAtMillis = null
                        )
                    }
                )
            }
            dao.deleteSentBefore(now.minus(SENT_HEADER_KEPT).toEpochMilli())
            late.isNotEmpty()
        }
    }

    override suspend fun purgeRoutes(): Outcome<Unit> = storage { dao.replaceRoutes(EMPTY_BLOB) }

    override suspend fun purgeHeartRates(): Outcome<Unit> = storage { dao.replaceHeartRates(EMPTY_BLOB) }

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> = storage {
        val rows = dao.pending()
        val oldestAllowed = now.minus(REMIND_AGE).toEpochMilli()
        rows.size >= REMIND_COUNT || rows.any { it.savedAtMillis <= oldestAllowed }
    }

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> {
        val revived =
            when (val appended = storage { appendInTransaction(recordId, samples) }) {
                is Outcome.Failure -> return appended
                is Outcome.Success -> appended.value ?: return Outcome.Failure(AppError.Rejected(RECORD_NOT_FOUND))
            }
        if (revived) uploadScheduler.requestUpload()
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> = storage { dao.deleteAll() }

    /** 덧붙이면 false, 머리 정보로 다시 만들었으면 true, 기록이 없으면 null. */
    private suspend fun appendInTransaction(recordId: String, samples: List<HeartRateSample>): Boolean? =
        database.withTransaction {
            val row = dao.find(recordId) ?: return@withTransaction null
            val merged = (decodeHeartRates(row.heartRates) + samples).distinctBy { it.at }.sortedBy { it.at }
            dao.upsert(row.copy(heartRates = encodeHeartRates(merged), sentAtMillis = null))
            row.sentAtMillis != null
        }

    private suspend fun <T> storage(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(AppError.Storage)
    }

    companion object {
        /** 늦은 심박을 받을 기록이 없음. */
        const val RECORD_NOT_FOUND = "RECORD_NOT_FOUND"

        private const val REMIND_COUNT = 20
        private val REMIND_AGE: Duration = Duration.ofDays(7)

        /** 보낸 기록 머리 정보를 남기는 시간. 워치 심박은 연결이 돌아오면 곧 오므로 하루면 충분하다(NFR-032). */
        private val SENT_HEADER_KEPT: Duration = Duration.ofDays(1)
        private val EMPTY_BLOB = ByteArray(0)
    }
}

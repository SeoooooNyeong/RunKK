package com.a207.runkk.app.data.store

import androidx.room.withTransaction
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.UploadScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 미전송 기록을 Room에 보관한다(6.1.14 data.store, D-54, FR-039). 저장 오류는 AppError.Storage로 바꿔 돌려준다(5.1.2).
 *
 * 늦은 심박(FR-043)의 자리: 측정이 끝난 뒤 온 심박은 RunTracker가 appendHeartRates로 넘긴다.
 * - 아직 보내지 않은 기록이면 그 기록의 심박에 시각 순서로 덧붙인다(다음 전송에 함께 간다).
 * - 이미 보내 remove된 기록이면, remove가 남겨 둔 머리 정보(경로·심박 없음, 최근 보낸 30건) + 늦은 심박으로 미전송 기록을
 *   다시 만들고 전송을 요청한다. 서버는 같은 기록ID를 "이미 저장됨"으로 받고(D-11), UploadPendingRunsUseCase가 심박만 덧붙여 보낸다.
 * - 머리 정보도 없으면(30건 밖이거나 탈퇴로 지움) 거부하지 않고 심박만 담은 항목(LateHeartRates)으로 남기고 전송을 요청한다.
 *   서버로 보내는 일은 UploadPendingRunsUseCase가 pendingLateHeartRates로 읽어 한다(D-77, v0.8 검증 R37: 이 클래스는 서버를 부르지 않는다).
 */
@Singleton
internal class RoomPendingRunRepository
@Inject
constructor(
    private val database: PendingRunDatabase,
    private val uploadScheduler: UploadScheduler,
    private val clock: Clock
) : PendingRunRepository {
    private val dao = database.pendingRunDao()
    private val lateDao = database.lateHeartRateDao()

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
     * 보낸 기록을 미전송 목록에서 뺀다. 경로·심박은 지우고 머리 정보만 최근 보낸 30건 안에서 남긴다(D-77).
     * 전송이 읽은 뒤 덧붙은 늦은 심박이 있으면 그 심박만 담은 미전송 기록으로 남기고 전송을 요청한다(F-4).
     */
    override suspend fun remove(recordId: String): Outcome<Unit> {
        val leftLate =
            when (val removed = storage { removeInTransaction(recordId) }) {
                is Outcome.Failure -> return removed
                is Outcome.Success -> removed.value
            }
        if (leftLate) requestUploadQuietly()
        return Outcome.Success(Unit)
    }

    /** 늦은 심박이 남았으면 참. */
    private suspend fun removeInTransaction(recordId: String): Boolean {
        val now = clock.instant()
        val handedOut = handedOutHeartRates[recordId]
        val leftLate = database.withTransaction {
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
            dao.keepLatestSent(SENT_HEADERS_KEPT)
            late.isNotEmpty()
        }
        // 트랜잭션이 끝난 뒤에만 지운다: 실패해 다시 remove할 때도 같은 기준으로 늦은 심박을 가린다.
        handedOutHeartRates.remove(recordId)
        return leftLate
    }

    override suspend fun purgeRoutes(): Outcome<Unit> = storage { dao.replaceRoutes(EMPTY_BLOB) }

    /** 미전송 기록의 심박과 늦은 심박 항목을 함께 지운다(FR-029, TF-VA-31). */
    override suspend fun purgeHeartRates(): Outcome<Unit> = storage {
        database.withTransaction {
            dao.replaceHeartRates(EMPTY_BLOB)
            lateDao.deleteAll()
        }
    }

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> = storage {
        val rows = dao.pending()
        val oldestAllowed = now.minus(REMIND_AGE).toEpochMilli()
        rows.size >= REMIND_COUNT || rows.any { it.savedAtMillis <= oldestAllowed }
    }

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> {
        val needsUpload =
            when (val appended = storage { appendInTransaction(recordId, samples) }) {
                is Outcome.Failure -> return appended
                is Outcome.Success -> appended.value
            }
        if (needsUpload) requestUploadQuietly()
        return Outcome.Success(Unit)
    }

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = storage {
        lateDao.all().map { LateHeartRates(it.recordId, decodeHeartRates(it.heartRates)) }
    }

    /**
     * 보낸 심박만 항목에서 뺀다(X-1). 전송이 읽은 뒤 mergeLateEntry로 합쳐진 심박은 남아 다음 전송에 간다.
     * 남은 심박이 없으면 항목을 지운다. 미전송 기록 쪽의 handedOutHeartRates와 같은 기준(측정 시각)으로 가린다.
     */
    override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> = storage {
        val sentAts = sent.samples.map { it.at }.toSet()
        database.withTransaction {
            val kept = lateDao.find(sent.recordId) ?: return@withTransaction
            val left = decodeHeartRates(kept.heartRates).filter { it.at !in sentAts }
            if (left.isEmpty()) {
                lateDao.delete(sent.recordId)
            } else {
                lateDao.upsert(LateHeartRateEntity(kept.recordId, encodeHeartRates(left), kept.savedAtMillis))
            }
        }
    }

    /** 미전송 기록·머리 정보·늦은 심박 항목을 모두 지운다(탈퇴). */
    override suspend fun clear(): Outcome<Unit> = storage {
        database.withTransaction {
            dao.deleteAll()
            lateDao.deleteAll()
        }
    }

    /**
     * 미전송 기록이면 덧붙인다(false). 머리 정보면 미전송 기록으로 다시 만든다(true). 둘 다 없으면 늦은 심박 항목에 합친다(true).
     * 참이면 전송을 요청해야 한다.
     */
    private suspend fun appendInTransaction(recordId: String, samples: List<HeartRateSample>): Boolean =
        database.withTransaction {
            val row = dao.find(recordId)
            if (row == null) {
                mergeLateEntry(recordId, samples)
                return@withTransaction true
            }
            dao.upsert(
                row.copy(
                    heartRates = encodeHeartRates(merge(decodeHeartRates(row.heartRates), samples)),
                    sentAtMillis = null
                )
            )
            row.sentAtMillis != null
        }

    private suspend fun mergeLateEntry(recordId: String, samples: List<HeartRateSample>) {
        val kept = lateDao.find(recordId)
        val merged = merge(kept?.let { decodeHeartRates(it.heartRates) }.orEmpty(), samples)
        val savedAt = kept?.savedAtMillis ?: clock.instant().toEpochMilli()
        lateDao.upsert(LateHeartRateEntity(recordId, encodeHeartRates(merged), savedAt))
    }

    /** 시각 순서로 합치고 같은 시각은 먼저 있던 것을 둔다. */
    private fun merge(kept: List<HeartRateSample>, added: List<HeartRateSample>): List<HeartRateSample> =
        (kept + added).distinctBy { it.at }.sortedBy { it.at }

    /** 전송 요청. 예약이 실패해도(WorkManager 미초기화 등) 보관은 이미 끝났으므로 다음 requestUpload 때 함께 간다. */
    private fun requestUploadQuietly() {
        try {
            uploadScheduler.requestUpload()
        } catch (e: IllegalStateException) {
            // 다음 전송 요청 때 함께 보낸다.
        }
    }

    private suspend fun <T> storage(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(AppError.Storage)
    }

    private companion object {
        const val REMIND_COUNT = 20
        val REMIND_AGE: Duration = Duration.ofDays(7)

        /**
         * 보낸 기록 머리 정보를 남기는 건수(D-77, E-24 제안값). 기간으로 자르면 하루 넘게 끊긴 워치의 심박이 사라지므로
         * 건수로 잘라 보관함 크기만 정한다(FR-043은 기간 제한이 없다).
         */
        const val SENT_HEADERS_KEPT = 30
        val EMPTY_BLOB = ByteArray(0)
    }
}

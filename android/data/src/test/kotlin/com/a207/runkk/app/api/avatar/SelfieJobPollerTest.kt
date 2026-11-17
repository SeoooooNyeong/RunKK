package com.a207.runkk.app.api.avatar

import androidx.work.ListenableWorker
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.BackgroundChoice
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieFailure
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.SelfieProgress
import com.a207.runkk.app.domain.avatar.SelfieResultNotifier
import com.a207.runkk.app.domain.avatar.Shop
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 셀카 작업 조회 워커 한 번의 실행(design-oo 05-30 6.1, 11절 4번): 간격·상한(접수 시각 기준)·연속 실패 3번·업무 거부 즉시 실패, 성공이면
 * 결과 적용 한 번과 사진 지움·알림, 실행 시간을 다 쓰면 이어 걸기. 시험 시계는 기다릴 때만 흐른다.
 */
class SelfieJobPollerTest {
    private val clock = FakeClock(1_000_000L)
    private val store = MemoryStore()
    private val tokens = TestTokens(jwtOf("7"))
    private val hub = SelfieProgressHub(store, SelfMember(tokens))
    private val photo = MemoryPhoto(byteArrayOf(1))
    private val notices = mutableListOf<Pair<String, Boolean>>()
    private val notifier = object : SelfieResultNotifier {
        override fun notify(jobId: String, succeeded: Boolean) {
            notices += jobId to succeeded
        }
    }
    private val waits = mutableListOf<Long>()
    private val repo = ScriptedAvatars(store)

    private fun poller() = SelfieJobPoller(repo, store, hub, photo, notifier, clock, SelfieJobPoller.POLL_DELAYS) {
        waits += it
        clock.now += it
    }

    @Test
    fun `성공이면 결과를 한 번 받아 적용하고 사진을 지운 뒤 성공 알림을 낸다(D-750, D-752)`() = runTest {
        store.save("job-1", clock.now)
        repo.jobs +=
            listOf(job(SelfieJob.Status.QUEUED), job(SelfieJob.Status.RUNNING), job(SelfieJob.Status.SUCCEEDED))

        assertEquals(SelfiePollEnd.FINISHED, poller().poll("job-1"))

        assertEquals(listOf(3_000L, 3_000L, 5_000L), waits)
        assertEquals(1, repo.finishes)
        assertNull(photo.saved)
        assertEquals(listOf("job-1" to true), notices)
    }

    @Test
    fun `실패(FAILED)면 사진을 지우고 실패 알림을 내며 결과를 받지 않는다`() = runTest {
        store.save("job-1", clock.now)
        repo.jobs += job(SelfieJob.Status.FAILED, "NO_FACE_DETECTED")

        poller().poll("job-1")

        assertEquals(0, repo.finishes)
        assertNull(photo.saved)
        assertEquals(listOf("job-1" to false), notices)
    }

    @Test
    fun `업무 거부는 바로 실패로 남긴다`() = runTest {
        store.save("job-1", clock.now)
        repo.failures += AppError.Rejected("JOB_NOT_FOUND")

        poller().poll("job-1")

        assertEquals(SelfieFailure("job-1", "JOB_NOT_FOUND"), store.failure())
        assertEquals(SelfieProgress.Failed("job-1", "JOB_NOT_FOUND"), hub.progress.value)
        assertEquals(listOf("job-1" to false), notices)
    }

    @Test
    fun `서버 실패(5xx)는 두 번까지 이어 가고 세 번 이어지면 실패다`() = runTest {
        store.save("job-1", clock.now)
        repo.failures += listOf(AppError.Server(503), AppError.Server(502))
        repo.jobs += job(SelfieJob.Status.RUNNING)
        repo.afterJobs = listOf(AppError.Server(500), AppError.Server(500), AppError.Server(500))

        poller().poll("job-1")

        assertEquals(SelfieProgress.Failed("job-1", null), hub.progress.value)
        assertEquals(6, repo.checks, "실패 둘 → 대기 성공으로 다시 셈 → 실패 셋")
    }

    @Test
    fun `연결 실패(오프라인)는 총 상한까지 이어 가고 실패로 세지 않는다(리뷰 R2-10)`() = runTest {
        store.save("job-1", clock.now)
        repo.afterJobs = List(10) { AppError.Network }
        repo.jobs += job(SelfieJob.Status.RUNNING)
        repo.forever = job(SelfieJob.Status.SUCCEEDED)

        poller().poll("job-1")

        assertEquals(listOf("job-1" to true), notices, "연결이 돌아오면 이어서 끝낸다")
    }

    @Test
    fun `조회하는 사이 기기 작업이 바뀌면(새 접수·기본 캐릭터) 사진을 지우지 않고 알리지 않는다(리뷰 R2-9)`() = runTest {
        store.save("job-1", clock.now)
        repo.onCheck = { store.save("job-2", clock.now) }
        repo.jobs += job(SelfieJob.Status.FAILED, "NO_FACE_DETECTED")

        poller().poll("job-1")

        assertTrue(notices.isEmpty())
        assertTrue(photo.saved != null, "새 작업의 사진은 그대로")
    }

    @Test
    fun `한 번의 실행이 8분을 넘기면 이어 걸도록 끝내고 결과를 내지 않는다`() = runTest {
        store.save("job-1", clock.now)
        repo.forever = job(SelfieJob.Status.RUNNING)

        assertEquals(SelfiePollEnd.CONTINUE, poller().poll("job-1"))

        assertTrue(clock.now - 1_000_000L >= SelfieJobPoller.RUN_LIMIT_MILLIS)
        assertTrue(notices.isEmpty())
        assertEquals("job-1", store.load())
    }

    @Test
    fun `다시 시작해도 총 상한은 접수 시각 기준이다(974초)`() = runTest {
        store.save("job-1", clock.now - SelfieJobPoller.TOTAL_LIMIT_MILLIS + 10_000L)
        repo.forever = job(SelfieJob.Status.RUNNING)

        assertEquals(SelfiePollEnd.FINISHED, poller().poll("job-1"))

        assertEquals(SelfieProgress.Failed("job-1", SelfieProgress.TIMEOUT), hub.progress.value)
        assertNull(store.load())
        assertEquals(listOf("job-1" to false), notices)
    }

    @Test
    fun `접수 시각을 모르면 지금을 접수 시각으로 남긴다`() = runTest {
        store.save("job-1", null)
        repo.jobs += job(SelfieJob.Status.SUCCEEDED)

        poller().poll("job-1")

        assertEquals(1_000_000L, repo.seenAt, "조회할 때 기기 사본에 접수 시각이 있다")
    }

    @Test
    fun `기기에 남긴 작업이 아니면(새 접수, 기본 캐릭터, 로그아웃) 아무것도 하지 않는다`() = runTest {
        store.save("job-2", clock.now)

        assertEquals(SelfiePollEnd.FINISHED, poller().poll("job-1"))

        assertEquals(0, repo.checks)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `워커 실행이 예외로 끝나면 백오프로 다시 걸고 세 번째부터 멈춘다(리뷰 R2-2)`() {
        assertEquals(ListenableWorker.Result.retry(), SelfieJobWorker.afterException(runAttemptCount = 0))
        assertEquals(ListenableWorker.Result.retry(), SelfieJobWorker.afterException(runAttemptCount = 1))
        assertEquals(ListenableWorker.Result.failure(), SelfieJobWorker.afterException(runAttemptCount = 2))
    }

    @Test
    fun `앞 화면 직접 확인 - 끝난 작업이면 기다리지 않고 결과를 받아 적용한다(D-820)`() = runTest {
        store.save("job-1", clock.now)
        hub.sync()
        repo.jobs += job(SelfieJob.Status.SUCCEEDED)

        poller().checkNow()

        assertTrue(waits.isEmpty(), "간격을 기다리지 않고 한 번 조회한다")
        assertEquals(1, repo.finishes)
        assertNull(store.load())
        assertNull(photo.saved)
        assertEquals(listOf("job-1" to true), notices)
    }

    @Test
    fun `앞 화면 직접 확인 - 실패로 끝났으면 실패를 남기고 만드는 중이면 그대로 둔다`() = runTest {
        store.save("job-1", clock.now)
        repo.jobs += job(SelfieJob.Status.FAILED, "NO_FACE_DETECTED")
        poller().checkNow()
        assertEquals(SelfieFailure("job-1", "NO_FACE_DETECTED"), store.failure())
        assertEquals(listOf("job-1" to false), notices)

        store.save("job-2", clock.now)
        repo.jobs += SelfieJob("job-2", SelfieJob.Status.RUNNING)
        poller().checkNow()
        assertEquals("job-2", store.load())
        assertEquals(1, notices.size)
    }

    @Test
    fun `앞 화면 직접 확인 - 기기에 남긴 작업이 없으면 묻지 않고 연결 실패는 실패로 남기지 않는다`() = runTest {
        poller().checkNow()
        assertEquals(0, repo.checks)

        store.save("job-1", clock.now)
        repo.failures += AppError.Network
        poller().checkNow()
        assertEquals("job-1", store.load())
        assertNull(store.failure())
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `워커 조회와 앞 화면 확인이 겹쳐도 결과는 한 번만 받고 한 번만 알린다`() = runTest {
        store.save("job-1", clock.now)
        repo.forever = job(SelfieJob.Status.SUCCEEDED)
        val gate = CompletableDeferred<Unit>()
        repo.finishGate = gate
        val poller = poller()

        val worker = launch { poller.poll("job-1") }
        runCurrent()
        val foreground = launch { poller.checkNow() }
        runCurrent()
        gate.complete(Unit)
        worker.join()
        foreground.join()

        assertEquals(1, repo.finishes)
        assertEquals(listOf("job-1" to true), notices)
        assertNull(store.failure())
    }

    private fun job(status: SelfieJob.Status, code: String? = null) = SelfieJob("job-1", status, code)
}

/** 접수 시각을 쓸 수 있는 시계. */
internal class FakeClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(now)

    override fun millis(): Long = now
}

/** 메모리 기기 사본. */
internal class MemoryStore : PendingSelfieStore {
    private var jobId: String? = null
    var at: Long? = null
    private var failed: SelfieFailure? = null

    override fun load(): String? = jobId

    override fun save(jobId: String, submittedAtMillis: Long?) {
        if (this.jobId != jobId || at == null) at = submittedAtMillis
        this.jobId = jobId
    }

    override fun submittedAt(): Long? = at?.takeIf { jobId != null }

    override fun clear() {
        jobId = null
        at = null
    }

    override fun failure(): SelfieFailure? = failed

    override fun saveFailure(failure: SelfieFailure) {
        failed = failure
    }

    override fun clearFailure() {
        failed = null
    }
}

internal class MemoryPhoto(var saved: ByteArray? = null) : PendingSelfiePhoto {
    override suspend fun save(jpeg: ByteArray) {
        saved = jpeg
    }

    override suspend fun load(): ByteArray? = saved

    override suspend fun clear() {
        saved = null
    }
}

/** 조회 응답을 차례로 돌려준다: 먼저 [failures], 그다음 [jobs], 그다음 [afterJobs], 끝나면 [forever]. */
private class ScriptedAvatars(private val store: PendingSelfieStore) : AvatarRepository {
    val failures = mutableListOf<AppError>()
    val jobs = mutableListOf<SelfieJob>()
    var afterJobs: List<AppError> = emptyList()
    var forever: SelfieJob? = null
    var checks = 0
    var finishes = 0
    var seenAt: Long? = null
    var onCheck: () -> Unit = {}
    var finishGate: CompletableDeferred<Unit>? = null

    override suspend fun selfieJob(jobId: String): Outcome<SelfieJob> {
        checks++
        seenAt = store.submittedAt()
        onCheck()
        failures.removeFirstOrNull()?.let { return failure(jobId, it) }
        if (jobs.isEmpty() && afterJobs.isNotEmpty()) {
            val next = afterJobs.first()
            afterJobs = afterJobs.drop(1)
            return failure(jobId, next)
        }
        jobs.removeFirstOrNull()?.let { job ->
            if (job.status == SelfieJob.Status.FAILED && store.load() == jobId) {
                store.clear()
                store.saveFailure(SelfieFailure(jobId, job.failureCode))
            }
            return Outcome.Success(job)
        }
        if (afterJobs.isNotEmpty()) {
            val next = afterJobs.first()
            afterJobs = afterJobs.drop(1)
            return failure(jobId, next)
        }
        return Outcome.Success(forever ?: error("no more replies"))
    }

    private fun failure(jobId: String, error: AppError): Outcome<SelfieJob> {
        if (error is AppError.Rejected && store.load() == jobId) store.clear()
        return Outcome.Failure(error)
    }

    override suspend fun finishSelfie(jobId: String): Outcome<Avatar> {
        finishes++
        finishGate?.await()
        store.clear()
        return Outcome.Success(Avatar("head:7:$jobId", null, "STAGE1", "DAYTIME", emptyMap(), 0, false, emptyMap()))
    }

    override suspend fun avatar(): Outcome<Avatar> = error("unused")

    override suspend fun submitSelfie(jpeg: ByteArray): Outcome<SelfieJob> = error("unused")

    override suspend fun pendingSelfieJobId(): String? = store.load()

    override suspend fun chooseDefault(characterId: String): Outcome<Avatar> = error("unused")

    override suspend fun equip(changes: Map<ItemSlot, Long?>): Outcome<Avatar> = error("unused")

    override suspend fun shop(slot: ItemSlot?): Outcome<Shop> = error("unused")

    override suspend fun purchase(itemId: Long): Outcome<Int> = error("unused")

    override suspend fun backgrounds(): Outcome<AvatarBackgrounds> = error("unused")

    override suspend fun chooseBackground(key: String?): Outcome<Unit> = error("unused")

    override suspend fun backgroundChoice(): Outcome<BackgroundChoice?> = error("unused")
}

/** 시험용 접근 토큰(회원 바뀜·로그아웃). */
internal class TestTokens(var access: String?) : TokenProvider {
    override fun accessToken(): String? = access

    override fun refreshToken(): String? = null

    override fun update(access: String, refresh: String) = Unit

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

package com.a207.runkk.app.data.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.run.RunApi
import com.a207.runkk.app.api.run.RunUploadApi
import com.a207.runkk.app.api.run.RunUploadRemoteImpl
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.data.store.PendingRunDatabase
import com.a207.runkk.app.data.store.RoomPendingRunRepository
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.UploadState
import com.a207.runkk.app.domain.sync.RetryPolicy
import com.a207.runkk.app.domain.sync.RunUploadRemote
import com.a207.runkk.app.domain.sync.UploadPendingRunsUseCase
import com.a207.runkk.app.domain.sync.UploadReceipt
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/**
 * UploadWorker(test 16.2 TC-5413, 16.5 통합 T-53 TC-5554·TC-5555): 실제 WorkManager 시험 도구로 재전송 예약을 보고, 출력 데이터
 * `rejected`는 이번 실행에서 서버가 거부한 건수다(D-461). TC-5554·TC-5555는 Room 보관함과 MockWebServer로 서버 응답부터 이어 본다.
 * 작업자는 Hilt EntryPoint로 의존을 꺼내므로 시험 Application이 그 그래프를 내준다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UploadWorkerTest.GraphApplication::class)
class UploadWorkerTest {
    private val now = Instant.parse("2026-11-02T01:00:00Z")
    private lateinit var context: Context
    private lateinit var db: PendingRunDatabase
    private lateinit var room: RoomPendingRunRepository
    private val api = ApiTestServer()

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        db = Room.inMemoryDatabaseBuilder(context, PendingRunDatabase::class.java).allowMainThreadQueries().build()
        room = RoomPendingRunRepository(db, NoUpload, Clock.fixed(now, ZoneOffset.UTC))
    }

    @AfterTest
    fun tearDown() {
        db.close()
        api.close()
    }

    @Test
    fun `TC-5413 (a) 실패가 없으면 재시도를 예약하지 않고 거부 건수는 0이다`() = runBlocking {
        room.save(run("r-1"))

        val result = work(room, ScriptedRemote(mapOf("r-1" to null)))

        assertEquals(0, result.rejected())
        assertEquals(emptyList(), retries())
    }

    @Test
    fun `TC-5413 (b) 거부 1건만 남으면 재시도를 예약하지 않고 거부 건수는 1이다`() = runBlocking {
        room.save(run("r-1"))

        val result = work(room, ScriptedRemote(mapOf("r-1" to AppError.Rejected("BAD_REQUEST"))))

        assertEquals(1, result.rejected())
        assertEquals(emptyList(), retries())
    }

    @Test
    fun `TC-5413 (c) 거부 1건과 네트워크 실패 1건이면 재시도 1회를 예약하고 거부 건수는 1이다`() = runBlocking {
        room.save(run("r-1"))
        room.save(run("r-2"))

        val result =
            work(room, ScriptedRemote(mapOf("r-1" to AppError.Rejected("BAD_REQUEST"), "r-2" to AppError.Network)))

        assertEquals(1, result.rejected())
        assertEquals(listOf(FIRST_RETRY), retries())
    }

    @Test
    fun `TC-5413 (d) 예외로 결과가 없으면 재시도 1회를 예약하고 거부 건수는 0이다`() = runBlocking {
        val broken =
            object : PendingRunRepository by room {
                override suspend fun pending(): Outcome<List<CompletedRun>> = throw IllegalStateException("깨짐")
            }

        val result = work(broken, ScriptedRemote(emptyMap()))

        assertEquals(0, result.rejected())
        assertEquals(listOf(FIRST_RETRY), retries())
    }

    @Test
    fun `TC-5554 서버가 400 PAUSE_OUT_OF_RANGE로 거부하면 사유와 함께 REJECTED로 남기고 재시도하지 않는다`() = runBlocking {
        room.save(run("r-1"))
        api.reply("""{"code":"PAUSE_OUT_OF_RANGE","message":"정지 구간이 기록 범위 밖이다"}""", code = 400)

        val result = work(room, serverRemote())

        val kept = (room.pending() as Outcome.Success).value.single()
        assertEquals(UploadState.REJECTED, kept.uploadState)
        assertEquals("PAUSE_OUT_OF_RANGE", kept.rejectedReason)
        assertEquals(1, result.rejected())
        assertEquals(emptyList(), retries())
    }

    @Test
    fun `TC-5555 서버 503이면 미전송으로 두고 재시도 1회를 RetryPolicy 간격으로 예약한다`() =
        assertRetried { api.reply("""{"code":"UNAVAILABLE"}""", code = 503) }

    @Test
    fun `TC-5555 서버 408이면 미전송으로 두고 재시도 1회를 예약한다`() =
        assertRetried { api.reply("""{"code":"REQUEST_TIMEOUT"}""", code = 408) }

    @Test
    fun `TC-5555 응답 없이 소켓 시간이 지나면 미전송으로 두고 재시도 1회를 예약한다`() = assertRetried {
        api.server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
    }

    @Test
    fun `TC-5555 인증 갱신에 실패해 401이면 미전송으로 두고 재시도 1회를 예약한다`() =
        assertRetried { api.reply("""{"code":"AUTH_FAILED"}""", code = 401) }

    private fun assertRetried(reply: () -> Unit) = runBlocking {
        room.save(run("r-1"))
        reply()

        val result = work(room, serverRemote())

        val kept = (room.pending() as Outcome.Success).value.single()
        assertEquals(UploadState.NOT_SENT, kept.uploadState)
        assertNull(kept.rejectedReason, "거부로 표시하지 않는다")
        assertEquals(0, result.rejected())
        assertEquals(listOf(FIRST_RETRY), retries())
    }

    private suspend fun work(pending: PendingRunRepository, remote: RunUploadRemote): ListenableWorker.Result {
        (context.applicationContext as GraphApplication).graph =
            Graph(UploadPendingRunsUseCase(pending, remote), WorkManagerUploadScheduler(context))
        return TestListenableWorkerBuilder<UploadWorker>(context).build().doWork()
    }

    private fun ListenableWorker.Result.rejected(): Int = outputData.getInt(UploadWorker.KEY_REJECTED, -1)

    /** 기다리는 재전송 작업의 지연(밀리초). */
    private fun retries(): List<Long> = WorkManager
        .getInstance(context)
        .getWorkInfosForUniqueWork(WorkManagerUploadScheduler.UNIQUE_WORK)
        .get()
        .filter { it.state == WorkInfo.State.ENQUEUED }
        .map { it.initialDelayMillis }

    /** 운영과 같은 변환기에 짧은 읽기 시간 제한을 붙인 전송 포트(MockWebServer). */
    private fun serverRemote(): RunUploadRemote {
        val client = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(api.server.url("/api/v1/"))
                .client(client)
                .addConverterFactory(api.json.asConverterFactory("application/json".toMediaType()))
                .build()
        return RunUploadRemoteImpl(retrofit.create<RunApi>(), retrofit.create<RunUploadApi>())
    }

    private fun run(id: String) = CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = now,
        elapsed = Duration.ofMinutes(10),
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = now
    )

    /** 기록별 결과: null이면 서버가 받는다. */
    private class ScriptedRemote(private val failures: Map<String, AppError?>) : RunUploadRemote {
        override suspend fun upload(run: CompletedRun): Outcome<UploadReceipt> =
            failures[run.recordId]?.let { Outcome.Failure(it) }
                ?: Outcome.Success(UploadReceipt(run.recordId, isAlreadySaved = false))

        override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
            Outcome.Success(Unit)
    }

    private object NoUpload : UploadScheduler {
        override fun requestUpload() = Unit
    }

    private class Graph(
        private val upload: UploadPendingRunsUseCase,
        private val scheduler: WorkManagerUploadScheduler
    ) : UploadWorker.Dependencies,
        GeneratedComponent {
        override fun uploadPendingRuns(): UploadPendingRunsUseCase = upload

        override fun retryPolicy(): RetryPolicy = RetryPolicy()

        override fun uploadScheduler(): WorkManagerUploadScheduler = scheduler
    }

    /** UploadWorker가 EntryPointAccessors.fromApplication으로 꺼내는 그래프를 내준다. */
    class GraphApplication :
        Application(),
        GeneratedComponentManager<Any> {
        var graph: Any = Unit

        override fun generatedComponent(): Any = graph
    }

    private companion object {
        /** RetryPolicy.nextDelay(0) = 30초(D-15). */
        val FIRST_RETRY: Long = Duration.ofSeconds(30).toMillis()
    }
}

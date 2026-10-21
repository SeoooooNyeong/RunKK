package com.a207.runkk.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** WorkManagerUploadScheduler(14장 "data.sync(WorkManager 시험 도구)"): 네트워크 조건의 고유 작업, 재전송 간격. */
@RunWith(RobolectricTestRunner::class)
class WorkManagerUploadSchedulerTest {
    private lateinit var context: Context
    private lateinit var scheduler: WorkManagerUploadScheduler

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        scheduler = WorkManagerUploadScheduler(context)
    }

    @Test
    fun `requestUpload는 네트워크 연결 조건의 고유 전송 작업 하나를 예약한다`() {
        scheduler.requestUpload()
        scheduler.requestUpload()

        val works = uniqueWorks().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, works.size, "같은 이름의 전송 작업은 하나만 기다린다")
        assertEquals(NetworkType.CONNECTED, works.single().constraints.requiredNetworkType)
        assertEquals(0L, works.single().initialDelayMillis)
    }

    @Test
    fun `재전송은 RetryPolicy가 준 간격 뒤에 돈다`() {
        scheduler.scheduleRetry(attempt = 3, delay = Duration.ofMinutes(4))

        val work = uniqueWorks().single { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(Duration.ofMinutes(4).toMillis(), work.initialDelayMillis)
    }

    @Test
    fun `재전송을 기다리는 중에 새 요청이 오면 바로 보낸다`() {
        scheduler.scheduleRetry(attempt = 5, delay = Duration.ofMinutes(30))

        scheduler.requestUpload()

        val waiting = uniqueWorks().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(listOf(0L), waiting.map { it.initialDelayMillis })
    }

    private fun uniqueWorks(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerUploadScheduler.UNIQUE_WORK).get()
}

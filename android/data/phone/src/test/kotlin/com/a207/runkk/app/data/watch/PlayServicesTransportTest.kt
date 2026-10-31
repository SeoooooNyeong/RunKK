package com.a207.runkk.app.data.watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Play 서비스 송수신(PlayServicesTransport) 시작 순서. E2E 실행 logcat에서 "applicationScope 작업 실패 NullPointerException
 * (Channel.iterator, WatchLink.kt)"를 찾았다: init 블록이 보낼 메시지 채널(outbox)보다 먼저 돌아, 시작 코루틴이 아직 null인 채널을
 * 읽고 죽었다. 그 뒤로는 워치로 표시 정보가 가지 않았다. 코루틴이 바로 도는 디스패처로 만들면 같은 순서를 재현한다.
 */
@RunWith(RobolectricTestRunner::class)
class PlayServicesTransportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `만들자마자 시작 코루틴이 돌아도 실패하지 않는다`() {
        val failures = mutableListOf<Throwable>()
        val scope =
            CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher() + CoroutineExceptionHandler { _, e -> failures += e }
            )
        try {
            val link = WatchLink(context, NoSettings, scope, java.time.Clock.systemUTC())
            link.publish(
                WatchDisplay(
                    isRunning = false,
                    elapsed = Duration.ZERO,
                    distance = Distance(0),
                    currentPace = null,
                    heartRateBpm = null,
                    todayPlan = null,
                    notice = null
                )
            )
        } finally {
            scope.cancel()
        }

        assertEquals(emptyList(), failures.map { it.toString() })
    }

    private object NoSettings : SettingsRepository {
        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

        override fun locationConsented() = false

        override fun heartRateConsented() = false

        override fun watchLinked() = false

        override fun courseMapShared() = false

        override fun birthYear(): Int? = null

        override fun guidanceSettings() = GuidanceSettings.DEFAULT

        override fun isGuest(): Boolean = false

        override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
}

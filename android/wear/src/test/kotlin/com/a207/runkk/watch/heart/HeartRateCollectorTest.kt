package com.a207.runkk.watch.heart

import com.a207.runkk.watch.heart.HeartRateCollector.State
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.failed
import com.a207.runkk.watch.testing.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 6.1.15 HeartRateCollector 상태 기계: 폰 허용 플래그·OS 권한·시작 요청·철회(FR-041, NFR-023). */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateCollectorTest {
    private val sensor = FakeHeartRateSensor()
    private val permission = FakeSensorPermission(isGranted = true)

    private fun TestScope.collector(): HeartRateCollector = HeartRateCollector(sensor, permission, backgroundScope)

    private fun TestScope.record(collector: HeartRateCollector): MutableList<WatchHeartRate> {
        val received = mutableListOf<WatchHeartRate>()
        backgroundScope.launch { collector.samples.collect { received += it } }
        runCurrent()
        return received
    }

    @Test
    fun `처음에는 폰 허용이 없어 막혀 있고 start해도 센서를 켜지 않는다`() = runTest {
        val collector = collector()
        assertEquals(State.BLOCKED, collector.state.value)

        collector.start()
        runCurrent()

        assertEquals(State.BLOCKED, collector.state.value)
        assertEquals(0, sensor.subscribers)
    }

    @Test
    fun `허용과 권한이 있으면 start에 센서를 켜고 값을 samples로 내보낸다`() = runTest {
        val collector = collector()
        val received = record(collector)

        collector.allow(true)
        assertEquals(State.STOPPED, collector.state.value)
        collector.start()
        runCurrent()
        sensor.emit(ok(1_000, 140))
        sensor.emit(failed(2_000))
        runCurrent()

        assertEquals(State.COLLECTING, collector.state.value)
        assertEquals(1, sensor.subscribers)
        assertEquals(listOf(ok(1_000, 140), failed(2_000)), received)
    }

    @Test
    fun `허용 플래그가 거짓으로 오면 즉시 센서를 끄고 이후 값을 내보내지 않는다`() = runTest {
        val collector = collector()
        val received = record(collector)
        collector.allow(true)
        collector.start()
        runCurrent()
        sensor.emit(ok(1_000, 140))
        runCurrent()

        collector.allow(false)
        runCurrent()

        assertEquals(State.BLOCKED, collector.state.value)
        assertEquals(0, sensor.subscribers)
        assertEquals(listOf(ok(1_000, 140)), received)
    }

    @Test
    fun `철회 뒤 다시 허용되어도 시작 요청이 남아 있으면 그때 다시 켠다`() = runTest {
        val collector = collector()
        collector.allow(true)
        collector.start()
        runCurrent()
        collector.allow(false)
        runCurrent()

        collector.allow(true)
        runCurrent()

        assertEquals(State.COLLECTING, collector.state.value)
        assertEquals(1, sensor.subscribers)
        assertEquals(2, sensor.openedSessions)
    }

    @Test
    fun `OS 권한이 없으면 권한 필요 상태이고 권한을 받은 뒤 수집한다`() = runTest {
        permission.isGranted = false
        val collector = collector()
        collector.allow(true)
        collector.start()
        runCurrent()

        assertEquals(State.NEEDS_PERMISSION, collector.state.value)
        assertEquals(0, sensor.subscribers)

        permission.isGranted = true
        collector.onPermissionChanged()
        runCurrent()

        assertEquals(State.COLLECTING, collector.state.value)
        assertEquals(1, sensor.subscribers)
    }

    @Test
    fun `OS 권한이 사라지면 수집을 멈춘다`() = runTest {
        val collector = collector()
        collector.allow(true)
        collector.start()
        runCurrent()

        permission.isGranted = false
        collector.onPermissionChanged()
        runCurrent()

        assertEquals(State.NEEDS_PERMISSION, collector.state.value)
        assertEquals(0, sensor.subscribers)
    }

    @Test
    fun `stop하면 센서를 끄고 다시 start할 수 있다`() = runTest {
        val collector = collector()
        collector.allow(true)
        collector.start()
        runCurrent()

        collector.stop()
        runCurrent()
        assertEquals(State.STOPPED, collector.state.value)
        assertEquals(0, sensor.subscribers)

        collector.start()
        runCurrent()
        assertEquals(State.COLLECTING, collector.state.value)
        assertEquals(2, sensor.openedSessions)
    }

    @Test
    fun `start를 거듭 불러도 측정 세션은 하나다`() = runTest {
        val collector = collector()
        collector.allow(true)
        repeat(3) { collector.start() }
        runCurrent()

        assertEquals(1, sensor.openedSessions)
    }

    @Test
    fun `센서 오류면 쓸 수 없음 상태가 되고 stop 뒤 start로 다시 시도한다`() = runTest {
        val collector = collector()
        collector.allow(true)
        collector.start()
        runCurrent()

        sensor.fail()
        runCurrent()
        assertEquals(State.UNAVAILABLE, collector.state.value)
        assertEquals(0, sensor.subscribers)

        collector.start()
        runCurrent()
        assertEquals(State.UNAVAILABLE, collector.state.value)

        collector.stop()
        collector.start()
        runCurrent()
        assertEquals(State.COLLECTING, collector.state.value)
    }
}

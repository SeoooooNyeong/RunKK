package com.a207.runkk.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRemote
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * DataStoreSettingsRepository(14장 단위(앱 데이터)): 사본이 없을 때 안전한 값, 동의 철회 때 changes 방출(D-53, NFR-023).
 * 기기 파일 대신 메모리 DataStore를 쓴다(Windows JVM은 DataStore 파일 교체(rename)가 실패해 시험이 기기마다 달라진다).
 * 같은 메모리 저장소를 새 인스턴스로 다시 열어 "앱 재시작 뒤에도 남는다"를 확인한다.
 */
class DataStoreSettingsRepositoryTest {
    private val store = MemoryDataStore()
    private val scopes = mutableListOf<CoroutineScope>()
    private val remote = QueueRemote()

    @AfterTest
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `사본이 없으면 개인정보 쪽으로 안전한 값을 돌려준다`() = runTest {
        val repository = open()

        assertEquals(SettingsSnapshot.SAFE, repository.changes().first())
        assertFalse(repository.locationConsented())
        assertFalse(repository.heartRateConsented())
        assertFalse(repository.watchLinked())
        assertFalse(repository.courseMapShared())
        assertNull(repository.birthYear())
        assertEquals(GuidanceSettings.DEFAULT, repository.guidanceSettings())
    }

    @Test
    fun `refresh는 서버 원본을 사본으로 저장하고 다시 열어도 남는다`() = runTest {
        val first = open()
        remote.next += Outcome.Success(CONSENTED)

        assertEquals(Outcome.Success(Unit), first.refresh())

        assertTrue(first.locationConsented())
        assertTrue(first.heartRateConsented())
        assertTrue(first.watchLinked())
        assertEquals(1990, first.birthYear())
        assertEquals(500, first.guidanceSettings().gapAlertMeters)
        assertEquals(Distance(3_000), first.guidanceSettings().freeRunTarget)
        closeAll()

        val reopened = open()
        assertEquals(CONSENTED, reopened.changes().first { it != SettingsSnapshot.SAFE })
        assertTrue(reopened.heartRateConsented())
    }

    @Test
    fun `동의를 철회하면 changes가 바로 철회된 사본을 내보낸다`() = runTest {
        val repository = open()
        remote.next += Outcome.Success(CONSENTED)
        repository.refresh()
        val seen = mutableListOf<SettingsSnapshot>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { repository.changes().collect { seen += it } }
        assertTrue(seen.last().isHeartRateConsented)

        remote.next += Outcome.Success(CONSENTED.copy(isHeartRateConsented = false))
        repository.refresh()

        val withdrawn = repository.changes().first { !it.isHeartRateConsented }
        assertFalse(withdrawn.isHeartRateConsented)
        assertFalse(repository.heartRateConsented())
        collecting.cancel()
        assertTrue(seen.any { !it.isHeartRateConsented && it.isWatchLinked }, "철회된 사본이 방출되지 않았다: $seen")
    }

    @Test
    fun `서버 조회가 실패하면 이전 사본을 지킨다`() = runTest {
        val repository = open()
        remote.next += Outcome.Success(CONSENTED)
        repository.refresh()
        remote.next += Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), repository.refresh())

        assertTrue(repository.heartRateConsented())
    }

    @Test
    fun `clear는 사본을 지워 안전한 값으로 돌아간다`() = runTest {
        val repository = open()
        remote.next += Outcome.Success(CONSENTED)
        repository.refresh()

        assertEquals(Outcome.Success(Unit), repository.clear())

        assertFalse(repository.locationConsented())
        assertEquals(SettingsSnapshot.SAFE, repository.changes().first())
    }

    private fun open(): DataStoreSettingsRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        return DataStoreSettingsRepository(store, remote, scope)
    }

    private fun closeAll() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private class QueueRemote : SettingsRemote {
        val next = ArrayDeque<Outcome<SettingsSnapshot>>()

        override suspend fun fetch(): Outcome<SettingsSnapshot> = next.removeFirst()
    }

    private companion object {
        val CONSENTED =
            SettingsSnapshot(
                isLocationConsented = true,
                isHeartRateConsented = true,
                isWatchLinked = true,
                isCourseMapShared = true,
                birthYear = 1990,
                guidanceSettings =
                    GuidanceSettings(
                        isRunVoiceOn = false,
                        isRunVibrationOn = true,
                        isBattleVoiceOn = true,
                        isBattleVibrationOn = false,
                        gapAlertMeters = 500,
                        isRankAlertOn = true,
                        shouldFollowCoachPlan = true,
                        freeRunTarget = Distance(3_000)
                    )
            )
    }
}

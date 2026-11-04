package com.a207.runkk.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.GuideCopy
import com.a207.runkk.app.domain.settings.ScreenTheme
import com.a207.runkk.app.domain.settings.SettingsRemote
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * DataStoreSettingsRepository(14장 단위(앱 데이터)): 사본이 없을 때 안전한 값, 동의 철회 때 changes 방출(D-53, NFR-023).
 * 기기 파일 대신 메모리 DataStore를 쓴다(Windows JVM은 DataStore 파일 교체(rename)가 실패해 시험이 기기마다 달라진다).
 * 같은 메모리 저장소를 새 인스턴스로 다시 열어 "앱 재시작 뒤에도 남는다"를 확인한다.
 * 로그아웃 경쟁(clear 뒤 이전 회원 사본이 되살아남)은 DataStore 방출과 저장 완료 시점을 시험이 쥐는 ControlledDataStore로
 * 결정적으로 재현한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `changes는 기기 사본을 읽기 전에는 내보내지 않아 저장된 테마 앞에 SYSTEM이 끼지 않는다(A4 첫 프레임)`() = runTest {
        val store = ControlledDataStore()
        remote.next += Outcome.Success(CONSENTED.copy(theme = ScreenTheme.DARK))
        DataStoreSettingsRepository(store, remote, backgroundScope).refresh()
        val restarted = DataStoreSettingsRepository(store, remote, backgroundScope)
        val seen = mutableListOf<ScreenTheme>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            restarted.changes().collect { seen += it.theme }
        }
        runCurrent()
        assertEquals(emptyList(), seen, "기기 사본을 읽기 전에 안전한 값(SYSTEM)을 내보냈다")

        // 기기 읽기 두 번(처음 연 저장소와 다시 연 저장소가 하나씩 받는다).
        store.deliver(store.stored)
        store.deliver(store.stored)
        runCurrent()

        assertEquals(listOf(ScreenTheme.DARK), seen)
    }

    @Test
    fun `기기 사본 읽기가 IOException 밖의 예외로 실패해도 안전한 값을 내보낸다(A4 리뷰)`() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = kotlinx.coroutines.flow.flow { throw IllegalStateException("깨진 사본") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                error("쓰지 않는다")
        }
        val repository = DataStoreSettingsRepository(broken, remote, backgroundScope)
        runCurrent()

        assertEquals(SettingsSnapshot.SAFE, repository.changes().first())
    }

    @Test
    fun `clear 뒤에 늦게 도착한 이전 사본 방출이 메모리 사본을 되살리지 않는다`() = runTest {
        val store = ControlledDataStore()
        val repository = DataStoreSettingsRepository(store, remote, backgroundScope)
        remote.next += Outcome.Success(CONSENTED)
        repository.refresh()
        val previousMember = store.stored

        assertEquals(Outcome.Success(Unit), repository.clear())
        // 기기 저장소 읽기(IO)가 늦어 clear 전에 저장된 이전 회원 사본이 이제야 온다.
        store.deliver(previousMember)
        runCurrent()

        assertFalse(repository.locationConsented())
        assertFalse(repository.heartRateConsented())
        assertFalse(repository.courseMapShared())
        assertEquals(SettingsSnapshot.SAFE, repository.changes().first())
    }

    @Test
    fun `저장 중인 refresh와 겹친 clear가 끝나면 이전 사본이 남지 않는다`() = runTest {
        val store = ControlledDataStore()
        val repository = DataStoreSettingsRepository(store, remote, backgroundScope)
        remote.next += Outcome.Success(CONSENTED)
        store.holdNextWrite()
        val refreshing = launch { repository.refresh() }
        runCurrent()

        val clearing = async { repository.clear() }
        runCurrent()
        store.release()
        refreshing.join()

        assertEquals(Outcome.Success(Unit), clearing.await())
        assertFalse(repository.locationConsented())
        assertEquals(SettingsSnapshot.SAFE, repository.changes().first())
        assertTrue(store.stored.asMap().isEmpty(), "기기 사본이 지워지지 않았다: ${store.stored}")
    }

    @Test
    fun `clear 뒤에 시작한 refresh는 새 회원 사본을 저장한다`() = runTest {
        val store = ControlledDataStore()
        val repository = DataStoreSettingsRepository(store, remote, backgroundScope)
        remote.next += Outcome.Success(CONSENTED)
        repository.refresh()
        repository.clear()
        remote.next += Outcome.Success(CONSENTED.copy(isCourseMapShared = false))

        assertEquals(Outcome.Success(Unit), repository.refresh())

        assertTrue(repository.locationConsented())
        assertFalse(repository.courseMapShared())
    }

    @Test
    fun `TC-6526 사본이 없으면 테마는 SYSTEM, 근접 알림은 참, 게스트는 거짓이다`() = runTest {
        val repository = open()

        assertEquals(ScreenTheme.SYSTEM, repository.changes().first().theme)
        assertTrue(repository.guidanceSettings().isProximityAlertOn)
        assertFalse(repository.isGuest())
    }

    @Test
    fun `TC-6527 테마가 DARK로 바뀌면 changes가 바로 내보내고 다시 열어도 DARK다`() = runTest {
        val repository = open()
        remote.next += Outcome.Success(CONSENTED)
        repository.refresh()
        val seen = mutableListOf<SettingsSnapshot>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { repository.changes().collect { seen += it } }

        remote.next += Outcome.Success(CONSENTED.copy(theme = ScreenTheme.DARK))
        repository.refresh()

        assertEquals(ScreenTheme.DARK, repository.changes().first { it.theme == ScreenTheme.DARK }.theme)
        collecting.cancel()
        assertTrue(seen.any { it.theme == ScreenTheme.DARK }, "DARK 사본이 방출되지 않았다: $seen")
        closeAll()
        val reopened = open()
        assertEquals(ScreenTheme.DARK, reopened.changes().first { it != SettingsSnapshot.SAFE }.theme)
    }

    @Test
    fun `근접 알림 끔과 게스트 여부를 사본에 저장하고 다시 열어도 남는다`() = runTest {
        val repository = open()
        val guest =
            CONSENTED.copy(
                guidanceSettings = CONSENTED.guidanceSettings.copy(isProximityAlertOn = false),
                isGuest = true
            )
        remote.next += Outcome.Success(guest)
        repository.refresh()

        assertFalse(repository.guidanceSettings().isProximityAlertOn)
        assertTrue(repository.isGuest())
        closeAll()
        val reopened = open()
        val stored = reopened.changes().first { it != SettingsSnapshot.SAFE }
        assertFalse(stored.guidanceSettings.isProximityAlertOn)
        assertTrue(stored.isGuest)
    }

    @Test
    fun `TC-6529 저장된 테마 이름을 모르면 SYSTEM으로 읽고 예외가 없다`() = runTest {
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[booleanPreferencesKey("has_copy")] = true
                this[booleanPreferencesKey("location_consented")] = true
                this[stringPreferencesKey("theme")] = "SEPIA"
            }
        }

        val repository = open()

        val stored = repository.changes().first { it.isLocationConsented }
        assertEquals(ScreenTheme.SYSTEM, stored.theme)
    }

    @Test
    fun `가이드 사본은 refresh가 덮지 않고 다시 열어도 남으며 clear가 지운다(D-277)`() = runTest {
        val first = open()
        assertEquals(GuideCopy.NONE, first.guideCopy())
        assertEquals(Outcome.Success(Unit), first.saveGuideCopy(GuideCopy.SEEN_UNSENT))
        remote.next += Outcome.Success(CONSENTED)
        first.refresh()
        assertEquals(GuideCopy.SEEN_UNSENT, first.guideCopy())
        closeAll()

        val reopened = open()
        assertEquals(GuideCopy.SEEN_UNSENT, reopened.guideCopy())
        reopened.clear()
        assertEquals(GuideCopy.NONE, reopened.guideCopy())
    }

    @Test
    fun `배치고사 완료 화면을 본 판은 refresh가 덮지 않고 다시 열어도 남으며 clear가 지운다(PL2-A2)`() = runTest {
        val first = open()
        assertEquals(null, first.placementCompleteSeen())
        assertEquals(Outcome.Success(Unit), first.savePlacementCompleteSeen(903))
        remote.next += Outcome.Success(CONSENTED)
        first.refresh()
        assertEquals(903L, first.placementCompleteSeen())
        closeAll()

        val reopened = open()
        assertEquals(903L, reopened.placementCompleteSeen())
        reopened.clear()
        assertEquals(null, reopened.placementCompleteSeen(), "로그아웃·탈퇴 뒤 다른 회원에게 남지 않는다")
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

    /**
     * 저장은 바로 반영하지만 data 방출은 시험이 deliver로 넘길 때만 한다(IO에서 도는 수집이 늦게 오는 상황).
     * holdNextWrite를 부르면 다음 저장 한 번이 release까지 끝나지 않는다(저장 중에 다른 연산이 끼어드는 상황).
     */
    private class ControlledDataStore : DataStore<Preferences> {
        var stored: Preferences = emptyPreferences()
            private set
        private val deliveries = Channel<Preferences>(Channel.UNLIMITED)
        private var gate: CompletableDeferred<Unit>? = null
        private var armed = false
        override val data: Flow<Preferences> = deliveries.receiveAsFlow()

        fun deliver(preferences: Preferences) {
            deliveries.trySend(preferences)
        }

        fun holdNextWrite() {
            gate = CompletableDeferred()
            armed = true
        }

        fun release() {
            gate?.complete(Unit)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (armed) {
                armed = false
                gate?.await()
            }
            return transform(stored).also { stored = it }
        }
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
                        isFollowingCoachPlan = true,
                        freeRunTarget = Distance(3_000)
                    )
            )
    }
}

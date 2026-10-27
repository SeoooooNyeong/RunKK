package com.a207.runkk.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.ScreenTheme
import com.a207.runkk.app.domain.settings.SettingsRemote
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 서버 설정 사본을 DataStore에 보관한다(6.1.14 data.settings, D-53). 앱에서 DataStore를 여는 것은 이 클래스 하나다.
 * 원본은 SettingsRemote(GET /me/settings)로 받고, 사본에는 서버 회원 설정 외의 값을 넣지 않는다.
 *
 * 읽기 연산은 위치 콜백·1초 시계에서 불리므로 막지 않는다: 저장된 사본을 메모리(StateFlow)에 올려 두고 읽는다.
 * 메모리 사본은 세대 번호와 함께 compare-and-set으로만 바꾸어, 로그아웃(clear) 뒤에 늦게 끝난 기기 읽기나 refresh가
 * 이전 회원의 동의·설정을 되살리지 못한다.
 * 사본이 없거나 아직 읽지 못했으면 개인정보 쪽으로 안전한 값(SettingsSnapshot.SAFE: 동의 거짓, 공유 거짓)이고 화면 테마는 SYSTEM,
 * 근접 알림은 참이다(서버 기본값과 같다, D-162). 저장된 테마 이름을 모르면 SYSTEM으로 읽는다(TC-6529).
 */
@Singleton
class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val remote: SettingsRemote,
    scope: CoroutineScope
) : SettingsRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        remote: SettingsRemote,
        scope: CoroutineScope
    ) : this(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE) }, remote, scope)

    /**
     * 메모리에 올린 사본과 세대 번호. 메모리 사본은 이 값 하나를 compare-and-set(update)으로만 바꾼다(clear 뒤에 늦게 온
     * 옛 값이 덮어쓰지 못하게, 로그아웃 경쟁).
     */
    private val cache = MutableStateFlow(Cache(epoch = 0, snapshot = null, isSettled = false))

    /** 기기 사본 쓰기(refresh)와 지우기(clear)를 차례로 돌린다: 기기 파일이 마지막 연산의 결과로 남게. */
    private val writing = Mutex()

    init {
        // 앱 시작 때 기기 사본을 한 번만 읽어 올린다. 이 클래스만 이 파일을 쓰므로 그 뒤 값은 refresh·clear가 직접 올린다.
        // 읽기가 늦게 끝나 그 사이 refresh나 clear가 이미 값을 정했으면(isSettled) 읽은 옛 값은 버린다.
        // 읽기가 어떤 예외로 실패해도 사본 없음(안전한 값)으로 정한다: changes()는 정해진 뒤에만 내보내므로 정하지 못하면
        // 첫 화면 테마를 끝내 받지 못한다(A4 리뷰 HIGH-1).
        scope.launch {
            val stored =
                try {
                    dataStore.data
                        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                        .first()
                        .toSnapshot()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "설정 사본 읽기 실패: 사본 없음으로 둔다")
                    null
                }
            cache.update { if (it.isSettled) it else it.copy(snapshot = stored, isSettled = true) }
        }
    }

    override suspend fun refresh(): Outcome<Unit> {
        val startedAt = cache.value.epoch
        val snapshot =
            when (val fetched = remote.fetch()) {
                is Outcome.Failure -> return fetched
                is Outcome.Success -> fetched.value
            }
        return writing.withLock {
            // clear 전에 시작한 refresh(이전 회원의 원본)는 쓰지 않는다.
            if (cache.value.epoch != startedAt) return@withLock Outcome.Success(Unit)
            try {
                dataStore.edit { it.write(snapshot) }
                // 저장하는 사이 clear가 세대를 올렸으면 메모리에도 올리지 않는다(뒤이어 clear가 기기 사본도 지운다).
                cache.update { if (it.epoch == startedAt) Cache(it.epoch, snapshot, isSettled = true) else it }
                Outcome.Success(Unit)
            } catch (e: IOException) {
                Outcome.Failure(AppError.Storage)
            }
        }
    }

    override fun locationConsented(): Boolean = current().isLocationConsented

    override fun heartRateConsented(): Boolean = current().isHeartRateConsented

    override fun watchLinked(): Boolean = current().isWatchLinked

    override fun courseMapShared(): Boolean = current().isCourseMapShared

    override fun birthYear(): Int? = current().birthYear

    override fun guidanceSettings(): GuidanceSettings = current().guidanceSettings

    override fun isGuest(): Boolean = current().isGuest

    /**
     * 기기 사본을 처음 읽기 전(isSettled 거짓)에는 내보내지 않는다(A4): 앱 시작 때 저장된 화면 테마 앞에 안전한 값(SYSTEM)이
     * 먼저 나가 첫 프레임 테마가 깜빡이지 않게, 동의 구독자(WatchLink)가 잠깐 철회로 보지 않게 한다. 사본이 없으면 그때 SAFE다.
     */
    override fun changes(): Flow<SettingsSnapshot> =
        cache.filter { it.isSettled }.map { it.snapshot ?: SettingsSnapshot.SAFE }.distinctUntilChanged()

    /**
     * 사본을 지운다. 세대를 올리고 메모리 사본을 먼저 안전한 값으로 돌린 뒤(기다리지 않고 바로) 기기 파일을 지운다.
     * 기기 파일을 지우지 못해도 메모리 사본은 안전한 값이다.
     */
    override suspend fun clear(): Outcome<Unit> {
        cache.update { Cache(it.epoch + 1, snapshot = null, isSettled = true) }
        return writing.withLock {
            try {
                dataStore.edit { it.clear() }
                Outcome.Success(Unit)
            } catch (e: IOException) {
                Outcome.Failure(AppError.Storage)
            }
        }
    }

    private fun current(): SettingsSnapshot = cache.value.snapshot ?: SettingsSnapshot.SAFE

    private fun Preferences.toSnapshot(): SettingsSnapshot? {
        if (this[HAS_COPY] != true) return null
        val defaults = GuidanceSettings.DEFAULT
        return SettingsSnapshot(
            isLocationConsented = this[LOCATION] ?: false,
            isHeartRateConsented = this[HEART_RATE] ?: false,
            isWatchLinked = this[WATCH_LINKED] ?: false,
            isCourseMapShared = this[COURSE_MAP_SHARED] ?: false,
            birthYear = this[BIRTH_YEAR],
            guidanceSettings =
                GuidanceSettings(
                    isRunVoiceOn = this[RUN_VOICE] ?: defaults.isRunVoiceOn,
                    isRunVibrationOn = this[RUN_VIBRATION] ?: defaults.isRunVibrationOn,
                    isBattleVoiceOn = this[BATTLE_VOICE] ?: defaults.isBattleVoiceOn,
                    isBattleVibrationOn = this[BATTLE_VIBRATION] ?: defaults.isBattleVibrationOn,
                    gapAlertMeters = this[GAP_ALERT_METERS]?.takeIf { it > 0 } ?: defaults.gapAlertMeters,
                    isRankAlertOn = this[RANK_ALERT] ?: defaults.isRankAlertOn,
                    isFollowingCoachPlan = this[FOLLOW_COACH_PLAN] ?: defaults.isFollowingCoachPlan,
                    freeRunTarget = this[FREE_RUN_TARGET_METERS]?.takeIf { it > 0 }?.let { Distance(it.toLong()) },
                    isProximityAlertOn = this[PROXIMITY_ALERT] ?: defaults.isProximityAlertOn
                ),
            theme = ScreenTheme.entries.firstOrNull { it.name == this[THEME] } ?: ScreenTheme.SYSTEM,
            isGuest = this[GUEST] ?: false
        )
    }

    private fun MutablePreferences.write(s: SettingsSnapshot) {
        clear()
        this[HAS_COPY] = true
        this[LOCATION] = s.isLocationConsented
        this[HEART_RATE] = s.isHeartRateConsented
        this[WATCH_LINKED] = s.isWatchLinked
        this[COURSE_MAP_SHARED] = s.isCourseMapShared
        s.birthYear?.let { this[BIRTH_YEAR] = it }
        val g = s.guidanceSettings
        this[RUN_VOICE] = g.isRunVoiceOn
        this[RUN_VIBRATION] = g.isRunVibrationOn
        this[BATTLE_VOICE] = g.isBattleVoiceOn
        this[BATTLE_VIBRATION] = g.isBattleVibrationOn
        this[GAP_ALERT_METERS] = g.gapAlertMeters
        this[RANK_ALERT] = g.isRankAlertOn
        this[FOLLOW_COACH_PLAN] = g.isFollowingCoachPlan
        g.freeRunTarget?.let { this[FREE_RUN_TARGET_METERS] = it.meters.toInt() }
        this[PROXIMITY_ALERT] = g.isProximityAlertOn
        this[THEME] = s.theme.name
        this[GUEST] = s.isGuest
    }

    /**
     * 메모리 사본(null이면 사본 없음)과 세대(clear마다 1 늘어난다). isSettled는 refresh·clear·첫 읽기 가운데 하나가
     * 값을 정했는지다: 정해진 뒤 끝난 첫 읽기는 버린다.
     */
    private data class Cache(val epoch: Long, val snapshot: SettingsSnapshot?, val isSettled: Boolean)

    private companion object {
        const val FILE = "settings_copy"
        val HAS_COPY = booleanPreferencesKey("has_copy")
        val LOCATION = booleanPreferencesKey("location_consented")
        val HEART_RATE = booleanPreferencesKey("heart_rate_consented")
        val WATCH_LINKED = booleanPreferencesKey("watch_linked")
        val COURSE_MAP_SHARED = booleanPreferencesKey("course_map_shared")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val RUN_VOICE = booleanPreferencesKey("run_voice")
        val RUN_VIBRATION = booleanPreferencesKey("run_vibration")
        val BATTLE_VOICE = booleanPreferencesKey("battle_voice")
        val BATTLE_VIBRATION = booleanPreferencesKey("battle_vibration")
        val GAP_ALERT_METERS = intPreferencesKey("gap_alert_meters")
        val RANK_ALERT = booleanPreferencesKey("rank_alert")
        val FOLLOW_COACH_PLAN = booleanPreferencesKey("follow_coach_plan")

        /** 자유런 목표 거리(m). 목표 없음(FREE)이면 넣지 않는다. */
        val FREE_RUN_TARGET_METERS = intPreferencesKey("free_run_target_meters")

        /** 근접 알림(FR-196), 화면 테마 이름(FR-203), 게스트 계정 여부(accountType GUEST, FR-199). */
        val PROXIMITY_ALERT = booleanPreferencesKey("proximity_alert")
        val THEME = stringPreferencesKey("theme")
        val GUEST = booleanPreferencesKey("guest")
    }
}

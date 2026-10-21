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
import androidx.datastore.preferences.preferencesDataStoreFile
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRemote
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 서버 설정 사본을 DataStore에 보관한다(6.1.14 data.settings, D-53). 앱에서 DataStore를 여는 것은 이 클래스 하나다.
 * 원본은 SettingsRemote(GET /me/settings)로 받고, 사본에는 서버 회원 설정 외의 값을 넣지 않는다.
 *
 * 읽기 연산은 위치 콜백·1초 시계에서 불리므로 막지 않는다: 저장된 사본을 메모리(StateFlow)에 올려 두고 읽는다.
 * 사본이 없거나 아직 읽지 못했으면 개인정보 쪽으로 안전한 값(SettingsSnapshot.SAFE: 동의 거짓, 공유 거짓)이다.
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

    /** 메모리에 올린 사본. null이면 사본 없음. */
    private val copy = MutableStateFlow<SettingsSnapshot?>(null)

    init {
        scope.launch {
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .collect { copy.value = it.toSnapshot() }
        }
    }

    override suspend fun refresh(): Outcome<Unit> {
        val snapshot =
            when (val fetched = remote.fetch()) {
                is Outcome.Failure -> return fetched
                is Outcome.Success -> fetched.value
            }
        return try {
            dataStore.edit { it.write(snapshot) }
            copy.value = snapshot
            Outcome.Success(Unit)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Storage)
        }
    }

    override fun locationConsented(): Boolean = current().locationConsented

    override fun heartRateConsented(): Boolean = current().heartRateConsented

    override fun watchLinked(): Boolean = current().watchLinked

    override fun courseMapShared(): Boolean = current().courseMapShared

    override fun birthYear(): Int? = current().birthYear

    override fun guidanceSettings(): GuidanceSettings = current().guidanceSettings

    override fun changes(): Flow<SettingsSnapshot> = copy.map { it ?: SettingsSnapshot.SAFE }.distinctUntilChanged()

    /** 사본을 지운다. 기기 파일을 지우지 못해도 메모리 사본은 안전한 값으로 돌린다. */
    override suspend fun clear(): Outcome<Unit> {
        copy.value = null
        return try {
            dataStore.edit { it.clear() }
            Outcome.Success(Unit)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Storage)
        }
    }

    private fun current(): SettingsSnapshot = copy.value ?: SettingsSnapshot.SAFE

    private fun Preferences.toSnapshot(): SettingsSnapshot? {
        if (this[HAS_COPY] != true) return null
        val defaults = GuidanceSettings.DEFAULT
        return SettingsSnapshot(
            locationConsented = this[LOCATION] ?: false,
            heartRateConsented = this[HEART_RATE] ?: false,
            watchLinked = this[WATCH_LINKED] ?: false,
            courseMapShared = this[COURSE_MAP_SHARED] ?: false,
            birthYear = this[BIRTH_YEAR],
            guidanceSettings =
                GuidanceSettings(
                    runVoice = this[RUN_VOICE] ?: defaults.runVoice,
                    runVibration = this[RUN_VIBRATION] ?: defaults.runVibration,
                    battleVoice = this[BATTLE_VOICE] ?: defaults.battleVoice,
                    battleVibration = this[BATTLE_VIBRATION] ?: defaults.battleVibration,
                    gapAlertMeters = this[GAP_ALERT_METERS]?.takeIf { it > 0 } ?: defaults.gapAlertMeters,
                    rankAlert = this[RANK_ALERT] ?: defaults.rankAlert,
                    followCoachPlan = this[FOLLOW_COACH_PLAN] ?: defaults.followCoachPlan
                )
        )
    }

    private fun MutablePreferences.write(s: SettingsSnapshot) {
        clear()
        this[HAS_COPY] = true
        this[LOCATION] = s.locationConsented
        this[HEART_RATE] = s.heartRateConsented
        this[WATCH_LINKED] = s.watchLinked
        this[COURSE_MAP_SHARED] = s.courseMapShared
        s.birthYear?.let { this[BIRTH_YEAR] = it }
        val g = s.guidanceSettings
        this[RUN_VOICE] = g.runVoice
        this[RUN_VIBRATION] = g.runVibration
        this[BATTLE_VOICE] = g.battleVoice
        this[BATTLE_VIBRATION] = g.battleVibration
        this[GAP_ALERT_METERS] = g.gapAlertMeters
        this[RANK_ALERT] = g.rankAlert
        this[FOLLOW_COACH_PLAN] = g.followCoachPlan
    }

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
    }
}

package com.a207.runkk.app.api.battle

import android.content.Context
import com.a207.runkk.app.domain.battle.PendingForfeit
import com.a207.runkk.app.domain.battle.PendingForfeitStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 남은 그만두기를 앱 전용 SharedPreferences에 둔다(S15P31A207-278). 대결 번호와 남긴 시각만 둔다. */
@Singleton
internal class PreferencesPendingForfeitStore @Inject constructor(@ApplicationContext context: Context) :
    PendingForfeitStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): PendingForfeit? {
        val since = prefs.getLong(KEY_SINCE, NONE).takeIf { it != NONE } ?: return null
        val battleId = prefs.getLong(KEY_BATTLE, NONE).takeIf { it != NONE }
        return PendingForfeit(battleId, Instant.ofEpochMilli(since))
    }

    override fun save(pending: PendingForfeit) {
        prefs.edit()
            .putLong(KEY_SINCE, pending.since.toEpochMilli())
            .putLong(KEY_BATTLE, pending.battleId ?: NONE)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "runkk_pending_forfeit"
        const val KEY_SINCE = "since"
        const val KEY_BATTLE = "battleId"
        const val NONE = -1L
    }
}

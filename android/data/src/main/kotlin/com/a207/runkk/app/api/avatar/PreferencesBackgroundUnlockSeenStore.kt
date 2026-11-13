package com.a207.runkk.app.api.avatar

import android.content.Context
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 전용 SharedPreferences에 회원 ID, 처음 기록 시각, 본 해금 배경 키만 둔다(W8 H-6). 다른 회원이면 없는 것으로 본다. */
@Singleton
internal class PreferencesBackgroundUnlockSeenStore
@Inject
constructor(
    @ApplicationContext context: Context,
    private val self: SelfMember
) : BackgroundUnlockSeenStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): BackgroundUnlockSeenStore.Seen? {
        val member = self.memberId() ?: return null
        if (prefs.getLong(KEY_MEMBER, NONE) != member) return null
        val since = prefs.getLong(KEY_SINCE, NONE).takeIf { it != NONE } ?: return null
        val keys = prefs.getStringSet(KEY_KEYS, emptySet()).orEmpty().toSet()
        return BackgroundUnlockSeenStore.Seen(keys, Instant.ofEpochMilli(since))
    }

    override fun save(seen: BackgroundUnlockSeenStore.Seen) {
        val member = self.memberId() ?: return
        prefs.edit()
            .putLong(KEY_MEMBER, member)
            .putLong(KEY_SINCE, seen.since.toEpochMilli())
            .putStringSet(KEY_KEYS, seen.keys.toSet())
            .apply()
    }

    private companion object {
        const val FILE = "runkk_background_unlock_seen"
        const val KEY_MEMBER = "memberId"
        const val KEY_SINCE = "since"
        const val KEY_KEYS = "keys"
        const val NONE = -1L
    }
}

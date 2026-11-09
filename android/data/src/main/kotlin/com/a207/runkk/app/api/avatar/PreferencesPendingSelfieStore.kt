package com.a207.runkk.app.api.avatar

import android.content.Context
import com.a207.runkk.app.api.core.SelfMember
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 전용 SharedPreferences에 회원 ID와 작업 ID만 둔다. 다른 회원으로 로그인했으면 없는 것으로 본다. */
@Singleton
internal class PreferencesPendingSelfieStore
@Inject
constructor(
    @ApplicationContext context: Context,
    private val self: SelfMember
) : PendingSelfieStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): String? {
        val member = self.memberId() ?: return null
        if (prefs.getLong(KEY_MEMBER, NONE) != member) return null
        return prefs.getString(KEY_JOB, null)
    }

    override fun save(jobId: String) {
        val member = self.memberId() ?: return
        prefs.edit().putLong(KEY_MEMBER, member).putString(KEY_JOB, jobId).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "runkk_pending_selfie"
        const val KEY_MEMBER = "memberId"
        const val KEY_JOB = "jobId"
        const val NONE = -1L
    }
}

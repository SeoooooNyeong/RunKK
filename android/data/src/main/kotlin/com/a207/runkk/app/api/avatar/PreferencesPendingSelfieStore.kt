package com.a207.runkk.app.api.avatar

import android.content.Context
import android.content.SharedPreferences
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.domain.avatar.SelfieFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전용 SharedPreferences에 회원 ID와 작업 ID·접수 시각, 마지막 실패(작업 ID·사유)만 둔다. 다른 회원으로 로그인했으면 없는 것으로 보고,
 * 다른 회원이 남기면 앞 회원 것을 지운다.
 */
@Singleton
internal class PreferencesPendingSelfieStore
@Inject
constructor(
    @ApplicationContext context: Context,
    private val self: SelfMember
) : PendingSelfieStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): String? = mine()?.getString(KEY_JOB, null)

    override fun save(jobId: String, submittedAtMillis: Long?) {
        val sameJob = load() == jobId
        val editor = edit() ?: return
        editor.putString(KEY_JOB, jobId)
        when {
            sameJob && prefs.contains(KEY_AT) -> Unit
            submittedAtMillis != null -> editor.putLong(KEY_AT, submittedAtMillis)
            else -> editor.remove(KEY_AT)
        }
        editor.apply()
    }

    override fun submittedAt(): Long? = mine()?.takeIf {
        it.contains(KEY_AT) && it.contains(KEY_JOB)
    }?.getLong(KEY_AT, 0L)

    override fun clear() {
        prefs.edit().remove(KEY_JOB).remove(KEY_AT).apply()
    }

    override fun failure(): SelfieFailure? {
        val stored = mine() ?: return null
        val jobId = stored.getString(KEY_FAILED_JOB, null) ?: return null
        return SelfieFailure(jobId, stored.getString(KEY_FAILED_CODE, null))
    }

    override fun saveFailure(failure: SelfieFailure) {
        edit()?.putString(KEY_FAILED_JOB, failure.jobId)?.putString(KEY_FAILED_CODE, failure.code)?.apply()
    }

    override fun clearFailure() {
        prefs.edit().remove(KEY_FAILED_JOB).remove(KEY_FAILED_CODE).apply()
    }

    /** 로그인한 회원이 남긴 것일 때만 읽는다. */
    private fun mine(): SharedPreferences? {
        val member = self.memberId() ?: return null
        return prefs.takeIf { it.getLong(KEY_MEMBER, NONE) == member }
    }

    /** 로그인한 회원 것으로 쓴다. 다른 회원이 남긴 것은 먼저 지운다. 로그아웃 상태면 남기지 않는다. */
    private fun edit(): SharedPreferences.Editor? {
        val member = self.memberId() ?: return null
        val editor = prefs.edit()
        if (prefs.getLong(KEY_MEMBER, NONE) != member) editor.clear()
        return editor.putLong(KEY_MEMBER, member)
    }

    private companion object {
        const val FILE = "runkk_pending_selfie"
        const val KEY_MEMBER = "memberId"
        const val KEY_JOB = "jobId"
        const val KEY_AT = "submittedAt"
        const val KEY_FAILED_JOB = "failedJobId"
        const val KEY_FAILED_CODE = "failedCode"
        const val NONE = -1L
    }
}

package com.a207.runkk.app.api.avatar

import android.content.Context
import com.a207.runkk.app.domain.sync.PreloadJournal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber

/**
 * 시작 에셋 미리 받기 기록(S15P31A207-345 2차, 포트 domain.sync.PreloadJournal). 표식은 Timber.d 한 줄("AssetPreload <종류> fetch …")로
 * 남겨 네트워크 로그와 함께 종류별 받기·적중을 센다. 회원 에셋을 다 받아 본 적이 있는지는 SharedPreferences에 남긴다(처음 설치 때만
 * 로딩 화면이 「처음 실행을 준비하고 있어요」를 보인다). 로그아웃·계정 바뀜에도 지우지 않는다.
 */
internal class PreferencesPreloadJournal @Inject constructor(@ApplicationContext private val context: Context) :
    PreloadJournal {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun mark(line: String) = Timber.d(line)

    override fun hasFinishedBefore(): Boolean = prefs.getBoolean(FINISHED, false)

    override fun markFinished() {
        if (!hasFinishedBefore()) prefs.edit().putBoolean(FINISHED, true).apply()
    }

    private companion object {
        const val PREFS = "runkk_asset_preload"
        const val FINISHED = "member_finished"
    }
}

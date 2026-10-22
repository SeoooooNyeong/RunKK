package com.a207.runkk.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

/**
 * 앱 프로세스 시작점(Hilt 루트, 6.1.14). 로깅은 Timber이고 디버그 빌드에서만 DebugTree를 심는다(릴리스는 기록하지 않는다).
 * 시작 작업(SyncDisposalsUseCase, SettingsRepository.refresh, PushTokenSync.syncCurrent, 워치 시작 화면 WatchHomeSync)과
 * 로그인 상태 구독은 AppStartup이 한다.
 */
@HiltAndroidApp
class RunkkApp : Application() {
    @Inject
    lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        startup.start()
    }
}

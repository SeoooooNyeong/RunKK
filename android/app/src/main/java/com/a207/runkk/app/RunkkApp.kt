package com.a207.runkk.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

/**
 * 앱 프로세스 시작점(Hilt 루트, 6.1.14). 로깅은 Timber이고 디버그 빌드에서만 DebugTree를 심는다(릴리스는 기록하지 않는다).
 * 시작 작업(SyncDisposalsUseCase, SettingsRepository.refresh, PushTokenSync.syncCurrent, 워치 시작 화면 WatchHomeSync)과
 * 로그인 상태 구독은 AppStartup이 한다. 카카오 SDK 초기화(로그인·카카오톡 공유 공용, D-275)는 KakaoSdkStartup이 한다.
 */
@HiltAndroidApp
class RunkkApp : Application() {
    @Inject
    lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // 카카오 로그인·공유가 함께 쓰는 SDK 초기화(D-275). 앱 키가 없으면 건너뛴다.
        if (!KakaoSdkStartup.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)) {
            Timber.w("카카오 앱 키가 없어 카카오 SDK를 초기화하지 않는다")
        }
        startup.start()
    }
}

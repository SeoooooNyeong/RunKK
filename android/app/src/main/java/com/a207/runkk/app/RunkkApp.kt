package com.a207.runkk.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 앱 프로세스 시작점(Hilt 루트, 6.1.14).
 * 시작 작업(SyncDisposalsUseCase, SettingsRepository.refresh, PushTokenSync.syncCurrent)과
 * 로그인 상태 구독은 포트 구현 바인딩이 생기는 A2 단계에서 applicationScope 위에 붙인다.
 */
@HiltAndroidApp
class RunkkApp : Application()

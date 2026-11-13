package com.a207.runkk.watch.ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** watch.ui 포트 ↔ Android 구현 바인딩. 포그라운드 서비스는 화면 패키지의 R·Activity를 쓰므로 여기에 둔다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchUiModule {
    @Binds
    abstract fun foregroundServiceLauncher(impl: AndroidForegroundServiceLauncher): ForegroundServiceLauncher

    @Binds
    abstract fun notificationPermissionGate(impl: AndroidNotificationPermissionGate): NotificationPermissionGate

    @Binds
    abstract fun locationSettingState(impl: AndroidLocationSettingState): LocationSettingState
}

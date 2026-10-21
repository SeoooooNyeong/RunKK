package com.a207.runkk.watch.ui

import android.app.Application
import com.a207.runkk.watch.link.PhoneLink
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 워치 앱 프로세스(Hilt 루트). 화면·폰 메시지·포그라운드 서비스 어느 것으로 떠도 여기서 시작한다:
 * 폰 연결([PhoneLink], 남은 운동 세션 정리 포함)을 열고, 수집 중 포그라운드 서비스 관리([CollectionForegroundKeeper])를 켠다.
 */
@HiltAndroidApp
class RunkkWatchApp : Application() {
    @Inject
    lateinit var phoneLink: PhoneLink

    @Inject
    lateinit var foregroundKeeper: CollectionForegroundKeeper

    override fun onCreate() {
        super.onCreate()
        phoneLink.start()
        foregroundKeeper.start()
    }
}

package com.a207.runkk.watch.ui

import android.app.Application
import com.a207.runkk.watch.link.PhoneLink
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** 워치 앱 프로세스(Hilt 루트). 시작할 때 폰 연결([PhoneLink])을 연다. */
@HiltAndroidApp
class RunkkWatchApp : Application() {
    @Inject
    lateinit var phoneLink: PhoneLink

    override fun onCreate() {
        super.onCreate()
        phoneLink.start()
    }
}

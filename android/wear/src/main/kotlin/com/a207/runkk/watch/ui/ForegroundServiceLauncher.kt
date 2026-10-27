package com.a207.runkk.watch.ui

import com.a207.runkk.watch.link.PhoneLink
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 수집 중 포그라운드 서비스를 띄우는 포트. 구현 [AndroidForegroundServiceLauncher]. */
interface ForegroundServiceLauncher {
    /** 서비스를 띄운다. 백그라운드 시작 제한 등으로 띄우지 못했으면 거짓. */
    fun launch(): Boolean
}

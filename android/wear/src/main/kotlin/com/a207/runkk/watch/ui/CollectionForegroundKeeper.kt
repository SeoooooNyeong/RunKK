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

/**
 * 심박을 모으는 동안만 포그라운드 서비스([HeartRateCollectionService], 타입 health)를 띄워 앱 프로세스가 쉽게 죽지 않게 한다.
 * 프로세스가 죽으면 폰의 수집 금지 플래그를 받지 못하기 때문이다(NFR-023).
 *
 * 수집을 켜고 끄는 규칙(폰 허용 플래그·러닝 여부·30분 침묵)은 그대로 PhoneLink가 정하고, 이 클래스는 그 결과인
 * [PhoneLink.heartRateCollecting]만 따른다. 서비스는 스스로 [awaitCollectionEnd]를 기다렸다 내려간다.
 * 밖에서 stopService를 부르지 않으므로 "startForegroundService 뒤 startForeground 전에 멈춤" 경합이 없다.
 */
@Singleton
class CollectionForegroundKeeper @Inject constructor(
    private val phoneLink: PhoneLink,
    private val launcher: ForegroundServiceLauncher,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var started = false

    /** 서비스를 띄웠거나 떠 있다. 서비스가 내려가며 [onServiceStopped]로 지운다. */
    private var serviceAlive = false

    /** 앱 프로세스가 시작될 때 한 번 부른다. 수집이 켜질 때마다 서비스가 없으면 띄운다. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch {
            phoneLink.heartRateCollecting.collect { collecting -> if (collecting) ensureService() }
        }
    }

    /** 화면이 앞으로 왔다. 백그라운드 시작 제한으로 못 띄운 서비스를 다시 띄운다. */
    fun onAppForeground() {
        if (phoneLink.heartRateCollecting.value) ensureService()
    }

    /** 서비스가 내려간다(수집 끝, startForeground 실패, 시스템이 멈춤). */
    fun onServiceStopped() {
        synchronized(lock) { serviceAlive = false }
    }

    /** 수집이 멈출 때까지 기다린다. 서비스는 이것이 돌아오면 내려간다. */
    suspend fun awaitCollectionEnd() {
        phoneLink.heartRateCollecting.first { collecting -> !collecting }
    }

    private fun ensureService() {
        synchronized(lock) {
            if (serviceAlive) return
            serviceAlive = launcher.launch()
        }
    }
}

package com.a207.runkk.app

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.sync.DeviceCopy
import com.a207.runkk.app.domain.sync.DeviceCopySync
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 앱 시작 작업과 로그인 상태 전환 작업(6.1.14 RunkkApp, 13.2, D-43·D-53·D-54). RunkkApp.onCreate가 start()를 한 번 부른다.
 * 기기 사본을 맞추는 일은 domain.sync의 DeviceCopySync가 하고(D-332), 이 클래스는 로그인 상태(SessionRepository.loggedIn)를 보고
 * 어느 연산을 부를지만 정한다. 모든 작업은 applicationScope 위에서 차례로 하고 실패는 기록만 한다(다음 시작·다음 로그인 때 다시 한다).
 *
 * - 시작: 워치 시작 화면 맞추기 시작(DeviceCopySync.start, FR-042) → 기기 사본(syncDevice: 파기 지시, 설정 사본, 푸시 토큰)
 *   → 로그인 상태면 회원 사본(syncAccount: 오늘 계획, 서버에 알리지 못한 첫 사용 가이드 끝냄, D-277)
 * - 로그인 상태가 참이 되면: 같은 작업을 다시 한다(로그아웃 상태로 시작했거나 세션이 만료됐다가 다시 로그인한 경우)
 * - 로그인 상태가 거짓이 되면: 회원 사본을 정리한다(clearAccount: 설정 사본 삭제, 오늘 계획 사본 비움)
 * 워치 시작 화면은 WatchHomeSync가 오늘 계획·설정 사본이 바뀔 때마다 다시 보낸다.
 */
@Singleton
class AppStartup
@Inject
constructor(
    private val deviceCopies: DeviceCopySync,
    private val session: SessionRepository,
    private val scope: CoroutineScope
) {
    fun start() {
        deviceCopies.start()
        scope.launch {
            // 시작 때 값을 먼저 잡아 두고, 그 뒤 값이 바뀔 때만 전환 작업을 한다(시작 작업과 겹쳐 두 번 하지 않는다).
            var previous = session.loggedIn.value
            syncAll()
            session.loggedIn.collect { loggedIn ->
                if (loggedIn == previous) return@collect
                previous = loggedIn
                if (loggedIn) syncAll() else report(deviceCopies.clearAccount())
            }
        }
    }

    private suspend fun syncAll() {
        report(deviceCopies.syncDevice())
        if (session.loggedIn.value) report(deviceCopies.syncAccount())
    }

    private fun report(failures: Map<DeviceCopy, AppError>) {
        failures.forEach { (copy, error) -> Timber.w("시작 작업 실패: %s (%s)", copy, error) }
    }
}

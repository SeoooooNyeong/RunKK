package com.a207.runkk.app

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.LoginState
import com.a207.runkk.app.domain.notification.PushTokenSync
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.sync.SyncDisposalsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 앱 시작 작업과 로그인 상태 전환 작업(6.1.14 RunkkApp, 13.2, D-43·D-53·D-54). RunkkApp.onCreate가 start()를 한 번 부른다.
 * 모든 작업은 applicationScope 위에서 차례로 하고 실패는 기록만 한다(다음 시작·다음 로그인 때 다시 한다).
 *
 * - 시작: 파기 지시 적용(SyncDisposalsUseCase) → 설정 사본 갱신(SettingsRepository.refresh) → 푸시 토큰 등록(PushTokenSync.syncCurrent)
 * - 로그인 상태가 참이 되면: 같은 세 작업을 다시 한다(로그아웃 상태로 시작했거나 세션이 만료됐다가 다시 로그인한 경우)
 * - 로그인 상태가 거짓이 되면: 설정 사본을 지운다(SettingsRepository.clear)
 */
@Singleton
class AppStartup
@Inject
constructor(
    private val syncDisposals: SyncDisposalsUseCase,
    private val settings: SettingsRepository,
    private val pushToken: PushTokenSync,
    private val loginState: LoginState,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            // 시작 때 값을 먼저 잡아 두고, 그 뒤 값이 바뀔 때만 전환 작업을 한다(시작 작업과 겹쳐 두 번 하지 않는다).
            var previous = loginState.loggedIn.value
            syncAll()
            loginState.loggedIn.collect { loggedIn ->
                if (loggedIn == previous) return@collect
                previous = loggedIn
                if (loggedIn) syncAll() else report("설정 사본 삭제", settings.clear())
            }
        }
    }

    private suspend fun syncAll() {
        report("파기 지시 적용", syncDisposals())
        report("설정 사본 갱신", settings.refresh())
        pushToken.syncCurrent()
    }

    private fun report(task: String, result: Outcome<Unit>) {
        if (result is Outcome.Failure) Timber.w("시작 작업 실패: %s (%s)", task, result.error)
    }
}

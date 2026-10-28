package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 탈퇴(FR-020, design-review.md E6): AccountRepository.withdraw → PendingRunRepository.clear →
 * SettingsRepository.clear → SessionRepository.clear. 서버 탈퇴가 실패하면 기기 자료를 지우지 않고 멈춘다.
 * 서버 탈퇴 뒤 기기 정리는 하나가 실패해도 나머지를 계속 지우고 첫 실패를 돌려준다.
 */
class WithdrawUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val pendingRuns: PendingRunRepository,
    private val settings: SettingsRepository,
    private val session: SessionRepository
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val withdrawn = account.withdraw()
        if (withdrawn is Outcome.Failure) return withdrawn
        val results = listOf(pendingRuns.clear(), settings.clear(), session.clear())
        return results.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}

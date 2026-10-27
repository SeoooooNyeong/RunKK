package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.settings.SettingsRepository
import javax.inject.Inject

/**
 * 로그아웃(FR-002, FR-199): AccountRepository.logout(최선 노력) → SessionRepository.clear → SettingsRepository.clear.
 * 서버 로그아웃이 실패해도(네트워크 없음) 기기 정리는 한다(D-159 (3)).
 * - 연동 회원: 미전송 기록은 지우지 않는다(다시 로그인해 보낼 수 있다).
 * - 연동하지 않은 게스트: 다시 로그인할 수 없으므로 미전송 기록도 지운다. ui.me가 자료 유실 고지·확인을 받은 뒤 부른다(FR-199).
 *   게스트인지는 서버 설정 사본의 계정 유형(accountType GUEST)으로 판정한다(A1 설계 갱신 요청 1 총괄 결정). 연동한 회원의 자격 증명이
 *   지워지지 않고 남아도 미전송 기록을 지우지 않는다. 연동 직후 설정 사본 갱신이 실패해 사본이 아직 GUEST인 경우에도 기록을 잃지 않도록
 *   기기의 게스트 자격 증명도 남아 있을 때만 게스트 갈래로 간다(연동 성공·ALREADY_LINKED에서 자격 증명을 지운다, A2 결정).
 * 기기 정리 하나가 실패해도 나머지를 계속하고 첫 실패를 돌려준다.
 */
class LogoutUseCase
@Inject
constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val pendingRuns: PendingRunRepository,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val isGuest = settings.isGuest() && session.hasGuestCredential()
        account.logout()
        val results =
            buildList {
                add(session.clear())
                if (isGuest) add(pendingRuns.clear())
                add(settings.clear())
            }
        return results.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}

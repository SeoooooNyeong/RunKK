package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.Outcome
import javax.inject.Inject

/**
 * 첫 사용 가이드를 띄울지와 끝냄 저장(D-277, FR-231). 기기 사본([SettingsRepository.guideCopy])을 먼저 보고, 서버 값은
 * [GuideRemote]로 읽고 쓴다. 로그인 상태는 부르는 쪽(ui.home)이 넘긴다: domain.settings는 domain.account를 읽지 않는다(5.1.2).
 */
class FirstRunGuide
@Inject
constructor(private val settings: SettingsRepository, private val remote: GuideRemote) {
    /**
     * 사본이 끝냄이면 거짓(서버 조회 없음). 로그인하지 않았으면 거짓. 그 밖에는 서버 seen을 읽어 참이면 사본을 맞추고 거짓,
     * 거짓이면 참이다. 서버를 못 읽으면 기기 사본만 보고 참이다(D-289).
     */
    suspend fun shouldShow(loggedIn: Boolean): Boolean {
        if (settings.guideCopy() != GuideCopy.NONE) return false
        if (!loggedIn) return false
        return when (val seen = remote.seen()) {
            is Outcome.Failure -> true

            is Outcome.Success -> {
                if (seen.value) settings.saveGuideCopy(GuideCopy.SEEN)
                !seen.value
            }
        }
    }

    /**
     * 가이드를 끝냈다("건너뛰기"·"시작하기"). 기기 사본에 먼저 끝냄을 쓰고 서버에 알린다. 서버가 실패해도 사본은 끝냄으로 남아
     * 가이드는 다시 뜨지 않고, 다음 시작 때 [resendCompletion]이 다시 보낸다.
     */
    suspend fun complete(): Outcome<Unit> {
        settings.saveGuideCopy(GuideCopy.SEEN_UNSENT)
        return send()
    }

    /** 앱 시작 때 부른다: 서버에 알리지 못한 끝냄이 있으면 다시 보낸다. 없으면 아무것도 하지 않는다. */
    suspend fun resendCompletion(): Outcome<Unit> =
        if (settings.guideCopy() == GuideCopy.SEEN_UNSENT) send() else Outcome.Success(Unit)

    private suspend fun send(): Outcome<Unit> {
        val sent = remote.markSeen()
        if (sent is Outcome.Success) settings.saveGuideCopy(GuideCopy.SEEN)
        return sent
    }
}

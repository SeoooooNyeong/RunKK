package com.a207.runkk.app.domain.avatar

import java.time.Instant

/**
 * 배경 해금 팝업(피그마 H-6)에서 이미 보인 해금 배경(설계 05-29: 서버에 "확인" 상태를 두지 않고 기기에 둔다, contract-W8.md 5.2).
 * 로그인한 회원마다 따로 둔다. 다른 회원으로 로그인했거나 처음이면 [load]가 null이다.
 */
interface BackgroundUnlockSeenStore {
    fun load(): Seen?

    fun save(seen: Seen)

    /**
     * @property keys 팝업으로 보였거나 처음 기록할 때 이미 해금돼 있던 배경 키
     * @property since 처음 기록한 시각. 이보다 먼저 해금한 배경은 새 해금으로 보지 않는다(설치·로그인 직후 팝업이 쏟아지지 않게)
     */
    data class Seen(val keys: Set<String>, val since: Instant)
}

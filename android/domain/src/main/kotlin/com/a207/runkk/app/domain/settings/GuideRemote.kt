package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.Outcome

/**
 * 첫 사용 가이드 서버 값 포트(GET /me/guide, PUT /me/guide, D-277, D-300). 구현은 SettingsRemote와 같은 api.account다.
 * 서버 값은 설정 응답(GET /me/settings)에 싣지 않는다(서버 D-244).
 */
interface GuideRemote {
    /** 이 회원이 가이드를 끝냈는가(GET /me/guide {seen}). */
    suspend fun seen(): Outcome<Boolean>

    /** 가이드를 끝냈다고 알린다(PUT /me/guide, 204 멱등). */
    suspend fun markSeen(): Outcome<Unit>
}

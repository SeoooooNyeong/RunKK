package com.a207.runkk.app.domain.avatar

/**
 * 접수한 셀카 작업을 화면 밖에서 끝까지 따라가게 맡긴다(design-oo 05-30 6.1, [임시:D-성-117]). 구현은 api.avatar
 * WorkManagerSelfieJobFollower다. 화면을 떠나거나 앱을 내려도 조회·결과 적용·알림이 이어진다.
 */
interface SelfieJobFollower {
    /** 새로 접수한 작업을 따라간다. 앞에 따라가던 작업은 그만둔다. */
    fun follow(jobId: String)

    /**
     * 기기에 남은 작업을 따라가는 중이 아니면 이어 따라간다(로그인한 앱 시작, 얼굴 만들기 다시 들어옴, 서버 /current로 찾은 작업). 따라가는
     * 중이면 그대로 둔다(돌고 있는 조회를 끊지 않는다). 구현마다 [follow]와 다르게 정해야 해서 기본 구현이 없다([임시:D-성-163]).
     */
    fun resume(jobId: String)

    /** 따라가기를 멈춘다(기본 캐릭터를 골랐을 때). */
    fun cancel()
}

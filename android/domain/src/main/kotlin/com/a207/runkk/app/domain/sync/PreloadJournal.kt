package com.a207.runkk.app.domain.sync

/**
 * 시작 에셋 미리 받기의 기록 포트(S15P31A207-345 2차, 구현 api.avatar). 받기·적중 표식 한 줄을 남기고(네트워크 로그로 종류별
 * 받기·적중을 센다, 예: "AssetPreload ranking fetch …"), 이 기기에서 회원 에셋을 다 받아 본 적이 있는지 기억한다(처음 설치 안내).
 */
interface PreloadJournal {
    fun mark(line: String)

    /** 이 기기에서 로그인한 회원 에셋을 한 번이라도 다 받았는가. */
    fun hasFinishedBefore(): Boolean

    /** 회원 에셋을 다 받았다고 남긴다(로그아웃·계정 바뀜에도 지우지 않는다). */
    fun markFinished()
}

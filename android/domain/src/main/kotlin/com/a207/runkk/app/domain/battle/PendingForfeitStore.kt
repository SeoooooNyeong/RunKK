package com.a207.runkk.app.domain.battle

/**
 * 보내지 못한 출발 전 그만두기(S15P31A207-278, 사용자 결정 2026-11-08 "기억했다가 복귀 때 재전송"). 매칭 대기·성사 화면에서 앱이 뒤로 가
 * 대결을 끝내야 하는데 백그라운드 네트워크 제한이나 느린 응답으로 그만두기를 보내지 못하면 기기에 남기고, 앱이 앞으로 올 때 다시 보낸다.
 * 프로세스가 끝나도 남는다. 한 번에 하나만 둔다(새로 남기면 덮어쓴다).
 */
interface PendingForfeitStore {
    /** 남은 그만두기. 없으면 null. */
    fun load(): PendingForfeit?

    fun save(pending: PendingForfeit)

    fun clear()
}

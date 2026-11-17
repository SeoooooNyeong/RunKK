package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender

/**
 * 중계 한 줄(합산 방식): 크루원 표시명·누적 거리·상태. 위치는 없다. [isMe]는 내 줄이다. [shownGender]는 그 크루원의 표시
 * 성별(D-327)로, 중계에는 없어 대기실 응답의 같은 memberId 줄에서 가져온다. 모르면 null(남자 기본 캐릭터).
 */
data class RelayRow(
    val name: String,
    val distance: Distance,
    val state: MateState = MateState.RUNNING,
    val isMe: Boolean = false,
    val shownGender: Gender? = null,
    /** 회원 ID(중계 줄). 트랙 보기에서 가장 가까운 크루원의 성별과 모습을 찾는다([임시:D-성-266]). 옛 서버면 null. */
    val memberId: Long? = null
) {
    /** 아직 달리는(정지·연결 확인 중 포함) 크루원인가. 끝낸 사람은 지도와 "함께 달리는 크루원"에서 뺀다. */
    val isRunning: Boolean get() = state != MateState.ENDED
}

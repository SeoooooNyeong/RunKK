package com.a207.runkk.app.domain.crew

/**
 * 열람자의 크루런 참여 상태(서버 crew.MyRunState). 크루런 목록·상세가 준다. 진행 참가자가 아니면 [NONE](투표·대기실만 입장 포함),
 * 측정 중이면 [RUNNING], 자기 측정을 끝냈으면 [ENDED]다. 옛 서버가 빼거나 모르는 값이면 [NONE]으로 본다.
 */
enum class MyRunState {
    NONE,
    RUNNING,
    ENDED
}

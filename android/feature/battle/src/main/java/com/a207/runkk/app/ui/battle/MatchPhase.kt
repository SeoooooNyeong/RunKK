package com.a207.runkk.app.ui.battle

/** 랜덤 매칭 단계: 거리 고르기 → 대기 → 성사(자동 출발까지). */
enum class MatchPhase { CHOOSING, REQUESTING, WAITING, CANCELLING, MATCHED }

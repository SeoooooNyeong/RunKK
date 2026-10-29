package com.a207.runkk.app.ui.battle

/**
 * 매칭 단계(D-269). 랜덤: 요청 → 대기(취소 중) → 성사. 친구 초대: 요청 → 초대 대기 → 성사 또는 초대 끝(거절·만료).
 * 친구 참가: 참가 확인 → 참가 중 → 성사. 거리 고르기는 배틀런 화면(BattleModeScreen)이 한다.
 */
enum class MatchPhase { REQUESTING, WAITING, INVITING, INVITE_ENDED, CONFIRMING, JOINING, CANCELLING, MATCHED }

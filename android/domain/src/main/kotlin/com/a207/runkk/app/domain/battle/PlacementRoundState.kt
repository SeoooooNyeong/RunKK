package com.a207.runkk.app.domain.battle

/**
 * 배치고사 판 셈 상태(서버 battle.PlacementRoundState, 05-03 5.7.3.1, D-487). COUNTED = 이 대결이 배치고사 판으로 셈,
 * NOT_COUNTED = 세지 않음(포기·무효 기록·기록 없이 만료·취소·배치고사가 이미 끝난 뒤 판정), PENDING = 아직 모름(판정·기록·셈 반영 전).
 */
enum class PlacementRoundState { COUNTED, NOT_COUNTED, PENDING }

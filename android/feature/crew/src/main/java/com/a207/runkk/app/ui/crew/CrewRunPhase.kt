package com.a207.runkk.app.ui.crew

/**
 * 크루런 화면 단계: 대기실 → 카운트다운 → 진행 중 → 끝(결과로). JOIN_CHOICE는 출발했지만 내가 진행 참가자가 아닐 때 「함께 뛰기」·
 * 「나가기」를 고르는 단계다(설계 05-32 [임시:D-성-883]).
 */
enum class CrewRunPhase { ENTERING, LOBBY, COUNTDOWN, JOIN_CHOICE, RUNNING, FINISHED, CANCELLED }

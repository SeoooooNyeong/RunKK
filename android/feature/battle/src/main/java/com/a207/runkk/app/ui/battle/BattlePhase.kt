package com.a207.runkk.app.ui.battle

/**
 * 대결 진행 단계: 시작 준비 → 카운트다운 → 측정 → 종료 카운트다운 → 끝. 시작하지 못하고 끝나면 FAILED.
 * START_FAILED는 누르자마자 연 셀프런·셀럽런 카운트다운이 대결 만들기 실패로 멈춘 상태다(다시 시도할 수 있다, S15P31A207-335 9회차).
 */
enum class BattlePhase { STARTING, COUNTDOWN, START_FAILED, RUNNING, CLOSING, FINISHED, FAILED }

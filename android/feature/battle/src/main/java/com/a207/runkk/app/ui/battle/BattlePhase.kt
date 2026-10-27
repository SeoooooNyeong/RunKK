package com.a207.runkk.app.ui.battle

/** 대결 진행 단계: 시작 준비 → 카운트다운 → 측정 → 종료 카운트다운 → 끝. 시작하지 못하고 끝나면 FAILED. */
enum class BattlePhase { STARTING, COUNTDOWN, RUNNING, CLOSING, FINISHED, FAILED }

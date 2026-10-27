package com.a207.runkk.app.ui.run

/** 러닝 화면 단계: 준비(재동의 대기) → 카운트다운 → 측정 ⇄ 잠금 → 종료 중. */
enum class RunPhase { PREPARING, COUNTDOWN, RUNNING, LOCKED, FINISHING }

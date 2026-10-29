package com.a207.runkk.app.ui.run

/** 러닝 화면 단계: 준비(재동의 대기) → 카운트다운 → 측정(정지 포함) → 종료 중. 앱 화면 잠금은 없다(D-256). */
enum class RunPhase { PREPARING, COUNTDOWN, RUNNING, FINISHING }

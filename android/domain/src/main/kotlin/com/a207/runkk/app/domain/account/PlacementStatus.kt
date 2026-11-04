package com.a207.runkk.app.domain.account

/** 배치 상태(서버 member.PlacementStatus). EXEMPT는 배치고사 없이 이미 티어가 있던 회원(브리프 12)이다. */
enum class PlacementStatus { IN_PROGRESS, COMPLETED, EXEMPT }

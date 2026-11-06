package com.a207.runkk.app.ui.home

import com.a207.runkk.app.domain.account.PlacementProgress

/** 배틀런 진입 시트 값(FR-284, D-499). [placement]는 홈이 읽은 배치 진행이고 못 읽었으면 null이다. */
data class BattleEntrySheetState(val placement: PlacementProgress?)

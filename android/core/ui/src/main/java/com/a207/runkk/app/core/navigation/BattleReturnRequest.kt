package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf

/**
 * 대결 진행 알림을 눌러 앱을 연 요청(S15P31A207-278). MainActivity가 알림 인텐트에서 [battleId]를 읽고 RunkkNavHost가 기능 화면 위
 * 안내(FeatureEntry.Overlay)에 [LocalBattleReturnRequest]로 넘긴다. 대결 기능이 그 대결 진행 화면을 열거나(이 프로세스가 그 회를
 * 열고 있을 때) 버린 뒤 [done]을 부른다.
 */
@Stable
class BattleReturnRequest(val battleId: Long, val done: () -> Unit)

/** 처리하지 않은 대결 진행 알림 요청. 없으면 null(미리보기·시험). */
val LocalBattleReturnRequest = compositionLocalOf<BattleReturnRequest?> { null }

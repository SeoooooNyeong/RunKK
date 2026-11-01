package com.a207.runkk.watch.battle

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 워치가 연 셀프·셀럽 기록 대결의 상대 기록을 프로세스 수명 동안 기억한다(코드 리뷰 WR13-2). BattleRunCoordinator는 앱 범위에서 대결을
 * 이어 가지만 기록 대결 종류·상대를 모른다. 화면(WatchFlowViewModel)을 다시 만들 때 이 값으로 셀프·셀럽 맥락을 되살려 결과 머리말이
 * BEST·GOOD·WIN·LOSE로 나오게 한다. 프로세스가 죽으면 대결 회도 함께 사라지므로 기기에 저장하지 않는다. WatchBattleFlow만 읽고 쓴다.
 */
@Singleton
class RecordBattleMemory @Inject constructor() {
    @Volatile
    var opponent: RecordOpponent? = null
}

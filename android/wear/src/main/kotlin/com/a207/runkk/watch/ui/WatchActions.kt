package com.a207.runkk.watch.ui

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.watch.cache.CachedTrack

/**
 * 워치 화면의 사용자 조작. [WatchActivity]가 단독 러닝 흐름은 [WatchFlowViewModel]에, 동행 표시(폰에서 시작한 러닝, FR-042)는
 * [WatchViewModel]에 잇는다(동행 표시의 끝내기·일시정지는 폰에 요청만 보낸다, C-002). 화면은 고른 조작만 쓴다. 미리보기·시험은 [NONE].
 */
data class WatchActions(
    val onModeSelected: (WatchMode) -> Unit = {},
    val onDistanceSelected: (Distance?) -> Unit = {},
    val onCelebritySelected: (CachedTrack) -> Unit = {},
    val onStartPressed: () -> Unit = {},
    val onCancelMatching: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onEndTapped: () -> Unit = {},
    val onEndLongPressed: () -> Unit = {},
    val onResultClosed: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onCompanionEndTapped: () -> Unit = {},
    val onCompanionEndLongPressed: () -> Unit = {},
    val onCompanionPauseTapped: () -> Unit = {},
    val onCompanionResumeTapped: () -> Unit = {}
) {
    companion object {
        /** 아무것도 하지 않는 조작(미리보기·스크린샷 시험). */
        val NONE = WatchActions()
    }
}

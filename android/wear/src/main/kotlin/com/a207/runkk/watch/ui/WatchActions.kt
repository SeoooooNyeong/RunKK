package com.a207.runkk.watch.ui

/** 워치 화면의 사용자 조작. [WatchActivity]가 [WatchViewModel]에 잇고, 화면은 고른 조작만 쓴다. 미리보기·시험은 [NONE]. */
data class WatchActions(
    val onStart: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onReconnect: () -> Unit,
    val onEndTapped: () -> Unit,
    val onEndLongPressed: () -> Unit,
    val onPauseTapped: () -> Unit,
    val onResumeTapped: () -> Unit
) {
    companion object {
        /** 아무것도 하지 않는 조작(미리보기·스크린샷 시험). */
        val NONE = WatchActions({}, {}, {}, {}, {}, {}, {})
    }
}

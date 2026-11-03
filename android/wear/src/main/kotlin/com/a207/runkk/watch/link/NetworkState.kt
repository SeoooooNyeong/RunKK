package com.a207.runkk.watch.link

import kotlinx.coroutines.flow.StateFlow

/**
 * 워치가 서버에 닿을 수 있는지(서버연결상태, 2.2, D-391 M76, J-6). 폰 경유(블루투스)·Wi-Fi·LTE를 모두 본다. 폰–워치 연결
 * (PhoneLink.phoneConnected)과 다르다. 화면이 배틀런 매칭 시작 막기(FR-251)와 셀프·셀럽 온라인·오프라인 고르기(D-377)에 쓴다.
 * 구현 [ConnectivityNetworkState].
 */
interface NetworkState {
    val isOnline: StateFlow<Boolean>
}

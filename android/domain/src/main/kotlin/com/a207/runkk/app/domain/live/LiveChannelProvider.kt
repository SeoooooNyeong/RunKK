package com.a207.runkk.app.domain.live

/**
 * 실시간 연결 하나를 임대로 나눠 준다(D-57, D-103). 서버는 회원마다 연결 하나만 두고 새 연결이 이전 연결을 닫으므로
 * 대결·크루런·채팅이 연결 하나를 함께 쓴다. open()은 그 연결에 대한 임대를 돌려주고, 임대의 close()는 자기 임대만 반납하며
 * 참조 수가 0이 될 때만 실제 연결을 닫는다. 구현 data.live.FallbackLiveChannelProvider: WebSocket을 먼저 열고 핸드셰이크가
 * 404·426이거나 3번 연속 실패하면 폴링 채널을 쓴다.
 */
interface LiveChannelProvider {
    fun open(): LiveChannel
}

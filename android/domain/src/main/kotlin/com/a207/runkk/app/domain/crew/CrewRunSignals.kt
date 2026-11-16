package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.domain.live.CrewRunChanged
import com.a207.runkk.app.domain.live.LiveChannelProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * 크루런 변경 신호(설계 05-32 [임시:D-성-877], [임시:D-성-878]). 기존 `/ws` JSON 연결(실패하면 기존 폴링 대체 채널)의 임대를 수집하는
 * 동안만 빌려 [CrewRunChanged]만 [CrewChange]로 바꿔 내보낸다. 수집을 멈추면(화면이 숨으면) 임대를 반납한다. 임대라 채팅·대결·크루런 진행 연결을 끊지
 * 않는다(D-103). 연결하지 못해도 오류를 내지 않는다: 놓친 변경은 당겨서 새로고침·화면 재진입으로 맞춘다(설계 4.3-6).
 */
class CrewRunSignals @Inject constructor(private val live: LiveChannelProvider) {
    fun changes(): Flow<CrewChange> = channelFlow {
        val channel = live.open()
        try {
            // 연결 직후 오는 신호를 잃지 않게 연결 전에 구독한다.
            launch(start = CoroutineStart.UNDISPATCHED) {
                channel.messages.collect { m -> if (m is CrewRunChanged) send(m.toChange()) }
            }
            channel.connect()
            awaitCancellation()
        } finally {
            channel.close()
        }
    }
}

/** 실시간 변경 메시지를 ui.crew가 보는 값으로 옮긴다(자원 이름은 같다). */
private fun CrewRunChanged.toChange(): CrewChange =
    CrewChange(crewId, postId, CrewChange.Resource.valueOf(resource.name), revision)

package com.a207.runkk.app.domain.live

import java.time.Instant

/**
 * 앱 → 서버 진행값(5.7.2). 항목 이름은 서버 realtime.InboundProgress의 JSON 항목과 같다
 * (channel, channelId, distanceMeters, elapsedSeconds, sentAt). 위치 필드가 없다(FR-089).
 */
data class ProgressReport(
    val channel: Channel,
    val channelId: Long,
    val distanceMeters: Long,
    val elapsedSeconds: Long,
    val sentAt: Instant
) : ClientMessage {
    enum class Channel {
        BATTLE,
        CREW_RUN
    }
}

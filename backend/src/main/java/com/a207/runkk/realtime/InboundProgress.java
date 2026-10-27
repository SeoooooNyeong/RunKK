package com.a207.runkk.realtime;

import com.a207.runkk.common.ProgressReport;

/**
 * 앱 → 서버 진행값 메시지의 전송 형식(설계 6.1.13, 5.7.2). 채널 구분과 채널 ID는 이 형식에만 있고(D-48) 나머지는
 * common.ProgressReport로 바꾼다. 위치 필드가 없다(FR-089).
 *
 * @param channel 대결 또는 크루런
 * @param channelId 대결 ID 또는 크루런 게시글 ID
 * @param report 거리·경과·보낸 시각
 */
public record InboundProgress(Channel channel, long channelId, ProgressReport report)
        implements InboundMessage {

    /** 진행값 채널. */
    public enum Channel {
        BATTLE,
        CREW_RUN
    }
}

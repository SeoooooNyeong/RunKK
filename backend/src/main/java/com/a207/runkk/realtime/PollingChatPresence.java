package com.a207.runkk.realtime;

import com.a207.runkk.crew.chat.ChatRoomPresence;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 폴링 대체 모드(runkk.live=polling)의 채팅방 입장 상태(설계 6.1.13, F.3.2, D-102). 늘 빈 집합이라 방 밖 알림 규칙이 작성자를 뺀 모든
 * 크루원에게 CREW_CHAT을 보내고, 앱은 열린 방의 알림을 표시하지 않는다(OpenChatRoomTracker). 앱 채팅방은 2초마다 ?after=로 읽는다.
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "polling")
public class PollingChatPresence implements ChatRoomPresence {

    @Override
    public Set<Long> presentMembers(long crewId) {
        return Set.of();
    }
}

package com.a207.runkk.realtime;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.battle.BattleMessenger;
import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.CrewRunMessenger;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * battle.BattleMessenger·crew.CrewRunMessenger의 폴링 우편함 구현(설계 6.1.13, C-004 대체 방안).
 * runkk.live=polling이면 WebSocketMessenger 대신 쓴다. 앱은 1초마다 GET /live/poll로
 * 꺼낸다(LivePollingController). 30초 넘은 메시지는 버린다.
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "polling")
public class PollingMessenger implements BattleMessenger, CrewRunMessenger {

    private final RealtimeMessageSerializer serializer;
    private final MessageMailbox mailbox;

    PollingMessenger(RealtimeMessageSerializer serializer, MessageMailbox mailbox) {
        this.serializer = serializer;
        this.mailbox = mailbox;
    }

    @Override
    public void send(long memberId, BattleMessage m) {
        mailbox.put(memberId, serializer.serialize(m));
    }

    @Override
    public void sendAll(Collection<Long> memberIds, BattleMessage m) {
        String json = serializer.serialize(m);
        memberIds.forEach(id -> mailbox.put(id, json));
    }

    @Override
    public void send(long memberId, CrewRunMessage m) {
        mailbox.put(memberId, serializer.serialize(m));
    }

    @Override
    public void sendAll(Collection<Long> memberIds, CrewRunMessage m) {
        String json = serializer.serialize(m);
        memberIds.forEach(id -> mailbox.put(id, json));
    }

    /** 쌓인 메시지 JSON을 들어온 순서대로 꺼낸다. */
    public List<String> drain(long memberId) {
        return mailbox.drain(memberId);
    }
}

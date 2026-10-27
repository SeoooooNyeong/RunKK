package com.a207.runkk.realtime;

import com.a207.runkk.crew.chat.ChatRoomPresence;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 크루 채팅방에 들어와 있는 회원을 기억한다(설계 6.1.13, crew.chat.ChatRoomPresence 구현, D-101, FR-209). 메모리 맵 crewId →
 * (memberId → 입장을 알린 소켓 세션 ID)이고 InboundChatPresence의 present가 참이면 넣고 거짓이면 뺀다. 소켓이 닫히면 그 세션이 남긴 입장만
 * 모든 방에서 지운다: 같은 회원의 새 연결이 먼저 들어와 다시 입장했으면 닫힌 옛 연결이 그 입장을 지우지 못한다(회원당 연결 하나, D-103). api 컨테이너 1개
 * 전제(5.4)이고 재시작하면 비워진다(앱이 재연결마다 다시 보낸다).
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class ChatPresenceRegistry implements ChatRoomPresence {

    /** 세션 하나가 확인할 수 있는 서로 다른 방 수(D-173). */
    static final int MAX_CHECKED_ROOMS = 10;

    private final Map<Long, Map<Long, String>> rooms = new ConcurrentHashMap<>();

    /** 세션 ID → (crewId → 구성원 확인을 통과했는가). 통과·거부 모두 기억해 같은 방을 다시 확인하지 않는다. */
    private final Map<String, Map<Long, Boolean>> checked = new ConcurrentHashMap<>();

    @Override
    public Set<Long> presentMembers(long crewId) {
        Map<Long, String> members = rooms.get(crewId);
        return members == null ? Set.of() : Set.copyOf(members.keySet());
    }

    /** 이 세션이 그 방을 이미 확인했는가(통과·거부 모두). */
    boolean isChecked(String sessionId, long crewId) {
        Map<Long, Boolean> rooms = checked.get(sessionId);
        return rooms != null && rooms.containsKey(crewId);
    }

    /** 이 세션이 그 방의 구성원 확인을 통과했는가. */
    boolean wasAllowed(String sessionId, long crewId) {
        Map<Long, Boolean> rooms = checked.get(sessionId);
        return rooms != null && Boolean.TRUE.equals(rooms.get(crewId));
    }

    /** 이 세션이 서로 다른 방을 하나 더 확인해도 되는가(10개 미만). */
    boolean canCheckAnother(String sessionId) {
        Map<Long, Boolean> rooms = checked.get(sessionId);
        return rooms == null || rooms.size() < MAX_CHECKED_ROOMS;
    }

    /** 확인 결과를 기억한다. 세션이 닫힐 때(leaveAll)까지 남는다. */
    void markChecked(String sessionId, long crewId, boolean allowed) {
        checked.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>()).put(crewId, allowed);
    }

    /** 방 입장(크루 구성원 확인은 부르는 쪽이 먼저 한다, CrewChatService.checkRoomAccess). */
    void enter(long crewId, long memberId, String sessionId) {
        rooms.compute(
                crewId,
                (id, members) -> {
                    Map<Long, String> room = members == null ? new ConcurrentHashMap<>() : members;
                    room.put(memberId, sessionId);
                    return room;
                });
    }

    /** 방 퇴장. 빈 방은 지운다. */
    void leave(long crewId, long memberId) {
        rooms.computeIfPresent(
                crewId,
                (id, members) -> {
                    members.remove(memberId);
                    return members.isEmpty() ? null : members;
                });
    }

    /** 소켓이 닫혔다: 그 세션이 남긴 입장만 모든 방에서 지운다. */
    void leaveAll(long memberId, String sessionId) {
        checked.remove(sessionId);
        rooms.keySet()
                .forEach(
                        crewId ->
                                rooms.computeIfPresent(
                                        crewId,
                                        (id, members) -> {
                                            members.remove(memberId, sessionId);
                                            return members.isEmpty() ? null : members;
                                        }));
    }
}

package com.a207.runkk.realtime;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 연결이 없는 회원에게 온 메시지를 30초 동안 둔다(설계 6.1.6 BattleMessenger.send 사후 조건). 재연결·폴링 때 받은 순서대로 꺼낸다. 회원마다 최대
 * 개수를 넘으면 오래된 것부터 버린다. 메모리에만 있다(인스턴스 하나, 5.4).
 */
@Component
class MessageMailbox {

    /** 우편함 보관 시간(6.1.6). */
    static final Duration KEEP = Duration.ofSeconds(30);

    /** 회원마다 보관 개수 상한(1초 진행값 30초 + 안내 여유). */
    static final int CAPACITY = 64;

    private final Map<Long, Deque<Letter>> boxes = new ConcurrentHashMap<>();
    private final Clock clock;

    MessageMailbox(Clock clock) {
        this.clock = clock;
    }

    void put(long memberId, String json) {
        Deque<Letter> box = boxes.computeIfAbsent(memberId, id -> new ArrayDeque<>());
        synchronized (box) {
            box.addLast(new Letter(json, LocalDateTime.now(clock)));
            while (box.size() > CAPACITY) {
                box.removeFirst();
            }
        }
    }

    /** 30초 안에 들어온 메시지를 들어온 순서대로 꺼내고 비운다. */
    List<String> drain(long memberId) {
        Deque<Letter> box = boxes.remove(memberId);
        if (box == null) {
            return List.of();
        }
        LocalDateTime oldest = LocalDateTime.now(clock).minus(KEEP);
        synchronized (box) {
            return box.stream()
                    .filter(letter -> !letter.at().isBefore(oldest))
                    .map(Letter::json)
                    .toList();
        }
    }

    private record Letter(String json, LocalDateTime at) {}
}

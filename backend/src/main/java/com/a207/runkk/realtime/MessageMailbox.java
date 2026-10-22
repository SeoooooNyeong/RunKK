package com.a207.runkk.realtime;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

    /** 넣으면서 30초 지난 것을 치운다. 꺼내기와 경합해도 잃지 않게 회원 칸 단위로 원자 처리한다. */
    void put(long memberId, String json) {
        LocalDateTime now = LocalDateTime.now(clock);
        boxes.compute(
                memberId,
                (id, box) -> {
                    Deque<Letter> letters = box == null ? new ArrayDeque<>() : box;
                    letters.addLast(new Letter(json, now));
                    prune(letters, now);
                    return letters;
                });
    }

    /** 30초 안에 들어온 메시지를 들어온 순서대로 꺼내고 비운다. */
    List<String> drain(long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> drained = new ArrayList<>();
        boxes.computeIfPresent(
                memberId,
                (id, box) -> {
                    prune(box, now);
                    box.forEach(letter -> drained.add(letter.json()));
                    return null;
                });
        return drained;
    }

    private static void prune(Deque<Letter> letters, LocalDateTime now) {
        LocalDateTime oldest = now.minus(KEEP);
        while (!letters.isEmpty()
                && (letters.size() > CAPACITY || letters.peekFirst().at().isBefore(oldest))) {
            letters.removeFirst();
        }
    }

    private record Letter(String json, LocalDateTime at) {}
}

package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.live.CrewRunRelay;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 진행 중인 크루런 참가자의 최신 거리(설계 6.1.7, srs.md 6.3 5.7.3, D-26). 1초 진행값은 DB에 쓰지 않고 세션 저장소(Redis)에만 둔다. 순위는
 * 그만두지 않은 참가자를 도착일시(먼저), 누적 거리(큰 순)로 정렬한다. 순위 음성 안내(FR-190)는 앱이 중계의 자기 순위로 한다(5.7.2 메시지 4종 고정).
 */
public class CrewRunLiveSession {

    /** 이 시간보다 오래 진행값이 없으면 "연결 확인 중"(FR-149 제안). */
    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final long postId;
    private final StandardDistance distance;
    private final LocalDateTime startAt;
    private final Map<Long, DisplayName> names;
    private final Map<Long, LiveProgress> latest = new HashMap<>();
    private final Map<Long, LocalDateTime> arrived = new HashMap<>();
    private final Set<Long> quitters = new HashSet<>();
    private final Set<Long> checking = new HashSet<>();

    private CrewRunLiveSession(
            long postId,
            StandardDistance distance,
            LocalDateTime startAt,
            Map<Long, DisplayName> names) {
        this.postId = postId;
        this.distance = distance;
        this.startAt = startAt;
        this.names = Map.copyOf(names);
    }

    /** 출발 때 진행 참가자 표시명으로 연다. */
    public static CrewRunLiveSession open(
            long postId,
            StandardDistance distance,
            Map<Long, DisplayName> names,
            LocalDateTime startAt) {
        return new CrewRunLiveSession(postId, distance, startAt, names);
    }

    /** 최신값 갱신 → 모든 참가자에게 순위 중계. 참가자가 아니거나 그만뒀으면 비어 있다. */
    public List<CrewRunMessage> onProgress(long memberId, ProgressReport r, LocalDateTime now) {
        if (!names.containsKey(memberId) || quitters.contains(memberId)) {
            return List.of();
        }
        LiveProgress before = latest.get(memberId);
        if (before != null && r.distance().compareTo(before.distance()) < 0) {
            // 늦게 도착한 이전 진행값: 연결 확인으로만 쓰고 거리는 되돌리지 않는다(B3 리뷰: 진행값 역행 거르기)
            latest.put(memberId, new LiveProgress(before.distance(), before.elapsed(), now));
        } else {
            latest.put(memberId, new LiveProgress(r.distance(), r.elapsed(), now));
        }
        checking.remove(memberId);
        if (r.distance().isAtLeast(distance.distance())) {
            arrived.putIfAbsent(memberId, now);
        }
        return List.of(relay());
    }

    /** 3초 넘게 진행값이 없는 참가자를 "연결 확인 중"으로 바꾸고, 바뀐 참가자가 있으면 중계 하나. */
    public List<CrewRunMessage> detectTimeouts(LocalDateTime now) {
        boolean changed = false;
        for (long memberId : names.keySet()) {
            if (quitters.contains(memberId)
                    || arrived.containsKey(memberId)
                    || checking.contains(memberId)) {
                continue;
            }
            LocalDateTime seen =
                    Optional.ofNullable(latest.get(memberId))
                            .map(LiveProgress::seenAt)
                            .orElse(startAt);
            if (Duration.between(seen, now).compareTo(TIMEOUT) > 0) {
                checking.add(memberId);
                changed = true;
            }
        }
        return changed ? List.of(relay()) : List.of();
    }

    /** 그만두지 않은 참가자를 도착일시(먼저), 누적 거리(큰 순)로. 그만둔 참가자는 끝에 순위 없이. */
    public List<RelayEntry> ranking() {
        List<Long> racing =
                names.keySet().stream()
                        .filter(id -> !quitters.contains(id))
                        .sorted(order())
                        .toList();
        List<RelayEntry> entries = new ArrayList<>();
        for (int i = 0; i < racing.size(); i++) {
            long id = racing.get(i);
            entries.add(
                    new RelayEntry(
                            names.get(id), distanceOf(id), i + 1, connectionOf(id), id, false));
        }
        quitters.stream()
                .sorted()
                .forEach(
                        id ->
                                entries.add(
                                        new RelayEntry(
                                                names.get(id),
                                                distanceOf(id),
                                                null,
                                                RelayEntry.Connection.QUIT,
                                                id,
                                                false)));
        return entries;
    }

    private Comparator<Long> order() {
        Comparator<Long> byArrival =
                Comparator.comparing(
                        (Long id) -> arrived.get(id),
                        Comparator.nullsLast(Comparator.naturalOrder()));
        return byArrival
                .thenComparing(this::distanceOf, Comparator.reverseOrder())
                .thenComparing(Comparator.naturalOrder());
    }

    private Distance distanceOf(long memberId) {
        return Optional.ofNullable(latest.get(memberId))
                .map(LiveProgress::distance)
                .orElse(Distance.ZERO);
    }

    private RelayEntry.Connection connectionOf(long memberId) {
        if (arrived.containsKey(memberId)) {
            return RelayEntry.Connection.ARRIVED;
        }
        return checking.contains(memberId)
                ? RelayEntry.Connection.CHECKING
                : RelayEntry.Connection.CONNECTED;
    }

    /** 그만둠 표시(FR-192). */
    public void markQuit(long memberId) {
        if (names.containsKey(memberId) && !arrived.containsKey(memberId)) {
            quitters.add(memberId);
            checking.remove(memberId);
        }
    }

    /** 지금 순위의 중계 메시지. */
    public CrewRunMessage relay() {
        return new CrewRunRelay(postId, ranking());
    }

    /** 진행 참가자 전원. */
    public Set<Long> participants() {
        return names.keySet();
    }

    public long postId() {
        return postId;
    }

    /** 세션 저장소용 값. */
    public Snapshot snapshot() {
        List<Snapshot.Runner> runners = new ArrayList<>();
        names.forEach(
                (id, name) -> {
                    Optional<LiveProgress> progress = Optional.ofNullable(latest.get(id));
                    runners.add(
                            new Snapshot.Runner(
                                    id,
                                    name.value(),
                                    progress.map(seen -> seen.distance().meters()).orElse(null),
                                    progress.map(seen -> seen.elapsed().toMillis()).orElse(null),
                                    progress.map(LiveProgress::seenAt).orElse(null),
                                    arrived.get(id),
                                    quitters.contains(id),
                                    checking.contains(id)));
                });
        return new Snapshot(postId, distance, startAt, runners);
    }

    /** snapshot으로 되살린다. */
    public static CrewRunLiveSession restore(Snapshot snapshot) {
        Map<Long, DisplayName> names = new HashMap<>();
        snapshot.runners().forEach(r -> names.put(r.memberId(), DisplayName.of(r.name())));
        CrewRunLiveSession session =
                new CrewRunLiveSession(
                        snapshot.postId(), snapshot.distance(), snapshot.startAt(), names);
        for (Snapshot.Runner runner : snapshot.runners()) {
            if (runner.meters() != null) {
                session.latest.put(
                        runner.memberId(),
                        new LiveProgress(
                                Distance.ofMeters(runner.meters()),
                                Duration.ofMillis(runner.elapsedMillis()),
                                runner.seenAt()));
            }
            if (runner.arrivedAt() != null) {
                session.arrived.put(runner.memberId(), runner.arrivedAt());
            }
            if (runner.quit()) {
                session.quitters.add(runner.memberId());
            }
            if (runner.checking()) {
                session.checking.add(runner.memberId());
            }
        }
        return session;
    }

    /**
     * 세션 저장 형식.
     *
     * @param postId 크루런 게시글 ID
     * @param distance 크루런 거리
     * @param startAt 시작 일시
     * @param runners 참가자 상태
     */
    public record Snapshot(
            long postId, StandardDistance distance, LocalDateTime startAt, List<Runner> runners) {

        public Snapshot {
            runners = List.copyOf(runners);
        }

        /**
         * 참가자 한 명의 상태.
         *
         * @param memberId 회원 ID
         * @param name 표시명
         * @param meters 최신 누적 거리, 받은 적 없으면 null
         * @param elapsedMillis 최신 경과 시간, 받은 적 없으면 null
         * @param seenAt 최신 진행값 받은 시각, 없으면 null
         * @param arrivedAt 도착 시각, 없으면 null
         * @param quit 그만둠
         * @param checking 연결 확인 중
         */
        public record Runner(
                long memberId,
                String name,
                Long meters,
                Long elapsedMillis,
                LocalDateTime seenAt,
                LocalDateTime arrivedAt,
                boolean quit,
                boolean checking) {}
    }
}

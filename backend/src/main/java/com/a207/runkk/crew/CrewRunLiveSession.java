package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.crew.live.CrewRunRelay;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 진행 중인 크루런 참가자의 최신 거리와 진행 상태(합산 방식, S15P31A207-236, 설계 6.1.7, D-26). 1초 진행값은 DB에 쓰지 않고 세션
 * 저장소(Redis)에만 둔다. 참가자는 각자 달리고 각자 멈추고 각자 끝낸다. 중계는 대기실 입장 순 참가자 줄과 크루가 함께 달린 거리 합이다.
 */
public class CrewRunLiveSession {

    /** 이 시간보다 오래 진행값이 없으면 "연결 확인 중"(FR-149 제안). */
    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final long postId;
    private final LocalDateTime startAt;

    /** 진행 참가자 표시명, 대기실 입장(참여) 순. */
    private final Map<Long, DisplayName> names;

    private final Map<Long, LiveProgress> latest = new HashMap<>();

    /** 일시정지한 참가자와 정지 때 경과. 이보다 큰 경과의 진행값이 오면 다시 달리는 중이다. */
    private final Map<Long, Duration> paused = new HashMap<>();

    private final Set<Long> ended = new HashSet<>();
    private final Set<Long> checking = new HashSet<>();

    private CrewRunLiveSession(long postId, LocalDateTime startAt, Map<Long, DisplayName> names) {
        this.postId = postId;
        this.startAt = startAt;
        this.names = new LinkedHashMap<>(names);
    }

    /** 출발 때 진행 참가자 표시명(입장 순으로 넘긴다)으로 연다. */
    public static CrewRunLiveSession open(
            long postId, Map<Long, DisplayName> names, LocalDateTime startAt) {
        return new CrewRunLiveSession(postId, startAt, names);
    }

    /**
     * 최신값 갱신 → 모든 참가자에게 중계. 참가자가 아니거나 이미 끝냈으면 비어 있다. 거리는 되돌리지 않고(늦게 온 이전 값), 정지 때보다 큰 경과가 오면 재개로
     * 본다.
     */
    public List<CrewRunMessage> onProgress(long memberId, ProgressReport r, LocalDateTime now) {
        if (!names.containsKey(memberId) || ended.contains(memberId)) {
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
        Duration pausedAt = paused.get(memberId);
        if (pausedAt != null && r.elapsed().compareTo(pausedAt) > 0) {
            paused.remove(memberId);
        }
        return List.of(relay());
    }

    /** 3초 넘게 진행값이 없는 달리는 참가자를 "연결 확인 중"으로 바꾸고, 바뀐 참가자가 있으면 중계 하나. 멈춘·끝낸 참가자는 보지 않는다. */
    public List<CrewRunMessage> detectTimeouts(LocalDateTime now) {
        boolean changed = false;
        for (long memberId : names.keySet()) {
            if (ended.contains(memberId)
                    || paused.containsKey(memberId)
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

    /** 일시정지. elapsedAt은 정지 때 경과이고 null이면 최신 진행값의 경과다. 참가자가 아니거나 끝냈으면 거짓. */
    public boolean markPaused(long memberId, Duration elapsedAt) {
        if (!names.containsKey(memberId) || ended.contains(memberId)) {
            return false;
        }
        Duration at =
                elapsedAt != null
                        ? elapsedAt
                        : Optional.ofNullable(latest.get(memberId))
                                .map(LiveProgress::elapsed)
                                .orElse(Duration.ZERO);
        paused.put(memberId, at);
        checking.remove(memberId);
        return true;
    }

    /** 끝냄. last가 있으면 최신값으로 남긴다(중계 거리와 결과가 같게). 참가자가 아니면 거짓. */
    public boolean markEnded(long memberId, LiveProgress last) {
        if (!names.containsKey(memberId)) {
            return false;
        }
        if (last != null) {
            latest.put(memberId, last);
        }
        ended.add(memberId);
        paused.remove(memberId);
        checking.remove(memberId);
        return true;
    }

    /** 참가자의 최신 진행값(끝낼 때·종료 때 참여에 남길 값). */
    public Optional<LiveProgress> latestOf(long memberId) {
        return Optional.ofNullable(latest.get(memberId));
    }

    /** 대기실 입장 순 중계 줄. */
    public List<RelayEntry> entries() {
        List<RelayEntry> entries = new ArrayList<>();
        names.forEach(
                (id, name) ->
                        entries.add(new RelayEntry(name, distanceOf(id), stateOf(id), id, false)));
        return entries;
    }

    /** 진행 참가자 전원(끝낸 사람 포함)의 최신 거리 합. */
    public Distance totalDistance() {
        return names.keySet().stream().map(this::distanceOf).reduce(Distance.ZERO, Distance::plus);
    }

    private Distance distanceOf(long memberId) {
        return Optional.ofNullable(latest.get(memberId))
                .map(LiveProgress::distance)
                .orElse(Distance.ZERO);
    }

    private RelayEntry.State stateOf(long memberId) {
        if (ended.contains(memberId)) {
            return RelayEntry.State.ENDED;
        }
        if (paused.containsKey(memberId)) {
            return RelayEntry.State.PAUSED;
        }
        return checking.contains(memberId) ? RelayEntry.State.CHECKING : RelayEntry.State.RUNNING;
    }

    /** 지금 상태의 중계 메시지. */
    public CrewRunMessage relay() {
        return new CrewRunRelay(postId, totalDistance(), entries());
    }

    /** 진행 참가자 전원. */
    public Set<Long> participants() {
        return names.keySet();
    }

    public long postId() {
        return postId;
    }

    /** 세션 저장소용 값. 참가자는 입장 순이다. */
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
                                    Optional.ofNullable(paused.get(id))
                                            .map(Duration::toMillis)
                                            .orElse(null),
                                    ended.contains(id),
                                    checking.contains(id)));
                });
        return new Snapshot(postId, startAt, runners);
    }

    /** snapshot으로 되살린다. */
    public static CrewRunLiveSession restore(Snapshot snapshot) {
        Map<Long, DisplayName> names = new LinkedHashMap<>();
        snapshot.runners().forEach(r -> names.put(r.memberId(), DisplayName.of(r.name())));
        CrewRunLiveSession session =
                new CrewRunLiveSession(snapshot.postId(), snapshot.startAt(), names);
        for (Snapshot.Runner runner : snapshot.runners()) {
            if (runner.meters() != null) {
                session.latest.put(
                        runner.memberId(),
                        new LiveProgress(
                                Distance.ofMeters(runner.meters()),
                                Duration.ofMillis(runner.elapsedMillis()),
                                runner.seenAt()));
            }
            if (runner.pausedAtMillis() != null) {
                session.paused.put(runner.memberId(), Duration.ofMillis(runner.pausedAtMillis()));
            }
            if (runner.ended()) {
                session.ended.add(runner.memberId());
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
     * @param startAt 시작 일시
     * @param runners 참가자 상태, 대기실 입장 순
     */
    public record Snapshot(long postId, LocalDateTime startAt, List<Runner> runners) {

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
         * @param pausedAtMillis 일시정지 때 경과, 달리는 중이면 null
         * @param ended 끝냄
         * @param checking 연결 확인 중
         */
        public record Runner(
                long memberId,
                String name,
                Long meters,
                Long elapsedMillis,
                LocalDateTime seenAt,
                Long pausedAtMillis,
                boolean ended,
                boolean checking) {}
    }
}

package com.a207.runkk.battle;

import com.a207.runkk.battle.live.BattleGuidance;
import com.a207.runkk.battle.live.OpponentProgress;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 진행 중인 랜덤 대결의 최신 진행값(설계 6.1.6, A-09). 대결마다 하나이고 LiveSessionStore(Redis 해시)에 둔다. 1초 진행값을 받아 상대에게 보낼
 * 메시지를 만들고, 추월(거리 차 10m 초과로 선두가 바뀜, FR-188)·도착(누적 ≥ 대결 거리)·끊김(3초, FR-090)을 판정한다.
 */
public final class LiveBattleSession {

    /** 추월로 보는 거리 차(흔들림 방지, FR-188). */
    static final long OVERTAKE_MARGIN_METERS = 10;

    /** 랜덤 대결 참가자 수. */
    static final int RUNNERS = 2;

    /** 진행값이 이 시간보다 오래 없으면 끊김(FR-090). */
    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final long battleId;
    private final Distance distance;
    private final Map<Long, Runner> runners;
    private Long leaderId;
    private Long newArrival;
    private Long reconnected;
    private final List<Long> timedOut = new ArrayList<>();

    private LiveBattleSession(
            long battleId, Distance distance, Map<Long, Runner> runners, Long leaderId) {
        if (runners.size() != RUNNERS) {
            throw new IllegalArgumentException("랜덤 대결 세션은 참가자 2명이다");
        }
        this.battleId = battleId;
        this.distance = distance;
        this.runners = runners;
        this.leaderId = leaderId;
    }

    /** 대결 시작 때 연다. 아직 진행값이 없는 참가자도 시작 뒤 3초가 지나면 끊김으로 본다. */
    public static LiveBattleSession open(
            long battleId, Distance distance, Map<Long, DisplayName> names, LocalDateTime now) {
        Map<Long, Runner> runners = new TreeMap<>();
        names.forEach((id, name) -> runners.put(id, Runner.start(id, name, now)));
        return new LiveBattleSession(battleId, distance, runners, null);
    }

    /** 저장소에서 다시 만든다(infra.battle). */
    public static LiveBattleSession restore(Snapshot s) {
        Map<Long, Runner> runners = new TreeMap<>();
        s.runners().forEach(r -> runners.put(r.memberId(), r));
        return new LiveBattleSession(
                s.battleId(), Distance.ofMeters(s.distanceMeters()), runners, s.leaderId());
    }

    public Snapshot snapshot() {
        return new Snapshot(battleId, distance.meters(), leaderId, List.copyOf(runners.values()));
    }

    /** 최신값 갱신 → 상대에게 OpponentProgress → 추월 판정 → 도착 판정 → 끊겼던 참가자면 복구 표시(A-09). 참가자가 아니면 빈 목록. */
    public List<BattleDelivery> onProgress(long memberId, ProgressReport r, LocalDateTime now) {
        newArrival = null;
        reconnected = null;
        Runner sender = runners.get(memberId);
        if (sender == null) {
            return List.of();
        }
        if (sender.disconnected()) {
            reconnected = memberId;
        }
        sender = sender.report(r, now);
        runners.put(memberId, sender);
        Runner rival = rivalOf(memberId);
        long gap = sender.distanceMeters() - rival.distanceMeters();
        List<BattleDelivery> out = new ArrayList<>();
        out.add(progressTo(rival.memberId(), sender, -gap, OpponentProgress.Connection.CONNECTED));
        judgeOvertake(memberId, rival.memberId(), gap, out);
        if (!sender.arrived() && sender.distanceMeters() >= distance.meters()) {
            runners.put(memberId, sender.arrive());
            newArrival = memberId;
        }
        return out;
    }

    private void judgeOvertake(long memberId, long rivalId, long gap, List<BattleDelivery> out) {
        if (Math.abs(gap) <= OVERTAKE_MARGIN_METERS) {
            return;
        }
        long newLeader = gap > 0 ? memberId : rivalId;
        long trailer = newLeader == memberId ? rivalId : memberId;
        if (leaderId != null && leaderId != newLeader) {
            runners.put(newLeader, runners.get(newLeader).overtook());
            long size = Math.abs(gap);
            out.add(guidance(newLeader, BattleGuidance.Kind.OVERTOOK, size));
            out.add(guidance(trailer, BattleGuidance.Kind.OVERTAKEN, size));
        }
        leaderId = newLeader;
    }

    /** 3초 넘게 진행값이 없는 참가자(도착 전)를 끊김으로 표시하고 상대에게 알린다(FR-090). */
    public List<BattleDelivery> detectTimeouts(LocalDateTime now) {
        timedOut.clear();
        List<BattleDelivery> out = new ArrayList<>();
        for (Runner runner : List.copyOf(runners.values())) {
            if (runner.arrived() || runner.disconnected() || !isSilent(runner, now)) {
                continue;
            }
            Runner silent = runner.disconnect();
            runners.put(silent.memberId(), silent);
            timedOut.add(silent.memberId());
            long gap = silent.distanceMeters() - rivalOf(silent.memberId()).distanceMeters();
            out.add(
                    progressTo(
                            rivalOf(silent.memberId()).memberId(),
                            silent,
                            -gap,
                            OpponentProgress.Connection.CHECKING));
        }
        return out;
    }

    private static boolean isSilent(Runner runner, LocalDateTime now) {
        return runner.lastSeen().plus(TIMEOUT).isBefore(now);
    }

    private BattleDelivery progressTo(
            long recipient, Runner about, long gap, OpponentProgress.Connection connection) {
        OpponentProgress message =
                new OpponentProgress(
                        battleId,
                        DisplayName.of(about.name()),
                        Distance.ofMeters(about.distanceMeters()),
                        Duration.ofMillis(about.elapsedMillis()),
                        gap,
                        connection);
        return new BattleDelivery(recipient, message);
    }

    private BattleDelivery guidance(long recipient, BattleGuidance.Kind kind, long gap) {
        return new BattleDelivery(recipient, new BattleGuidance(battleId, kind, gap));
    }

    private Runner rivalOf(long memberId) {
        return runners.values().stream()
                .filter(r -> r.memberId() != memberId)
                .findFirst()
                .orElseThrow();
    }

    /** 방금 도착한 참가자(서비스가 Battle.recordArrival을 부른다). onProgress 한 번마다 다시 정해진다. */
    public Optional<Long> newArrival() {
        return Optional.ofNullable(newArrival);
    }

    /** 방금 끊김에서 돌아온 참가자(서비스가 Battle.markReconnected를 부른다). */
    public Optional<Long> reconnected() {
        return Optional.ofNullable(reconnected);
    }

    /** 방금 detectTimeouts가 끊김으로 표시한 참가자(서비스가 Battle.markDisconnected를 부른다). */
    public List<Long> timedOut() {
        return List.copyOf(timedOut);
    }

    /** 카운트다운 종료 때 Battle.recordOvertakes로 옮긴다. */
    public Map<Long, Integer> overtakeCounts() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        runners.values().forEach(r -> counts.put(r.memberId(), r.overtakes()));
        return counts;
    }

    public long battleId() {
        return battleId;
    }

    /** 참가자 표시명(카운트다운 도착자 이름). */
    public Optional<DisplayName> nameOf(long memberId) {
        return Optional.ofNullable(runners.get(memberId)).map(r -> DisplayName.of(r.name()));
    }

    /**
     * 저장 형식(LiveSessionStore 구현이 직렬화한다).
     *
     * @param battleId 대결 ID
     * @param distanceMeters 대결 거리
     * @param leaderId 선두 회원, 아직 없으면 null
     * @param runners 참가자 2명
     */
    public record Snapshot(
            long battleId, long distanceMeters, Long leaderId, List<Runner> runners) {

        public Snapshot {
            runners = List.copyOf(runners);
        }
    }

    /**
     * 참가자 한 명의 최신 상태.
     *
     * @param memberId 회원
     * @param name 표시명
     * @param distanceMeters 최신 누적 거리
     * @param elapsedMillis 최신 경과 시간
     * @param lastSeen 마지막으로 진행값을 받은 시각
     * @param overtakes 추월 횟수
     * @param arrived 도착했는가
     * @param disconnected 끊김 표시 중인가
     */
    public record Runner(
            long memberId,
            String name,
            long distanceMeters,
            long elapsedMillis,
            LocalDateTime lastSeen,
            int overtakes,
            boolean arrived,
            boolean disconnected) {

        static Runner start(long memberId, DisplayName name, LocalDateTime now) {
            return new Runner(memberId, name.value(), 0, 0, now, 0, false, false);
        }

        /** 늦게 도착한 이전 진행값(더 작은 거리)은 연결 확인으로만 쓰고 거리·경과는 되돌리지 않는다(B3 리뷰: 진행값 역행 거르기). */
        Runner report(ProgressReport r, LocalDateTime now) {
            return new Runner(
                    memberId,
                    name,
                    Math.max(distanceMeters, r.distance().meters()),
                    Math.max(elapsedMillis, r.elapsed().toMillis()),
                    now,
                    overtakes,
                    arrived,
                    false);
        }

        Runner overtook() {
            return new Runner(
                    memberId,
                    name,
                    distanceMeters,
                    elapsedMillis,
                    lastSeen,
                    overtakes + 1,
                    arrived,
                    disconnected);
        }

        Runner arrive() {
            return new Runner(
                    memberId,
                    name,
                    distanceMeters,
                    elapsedMillis,
                    lastSeen,
                    overtakes,
                    true,
                    false);
        }

        Runner disconnect() {
            return new Runner(
                    memberId,
                    name,
                    distanceMeters,
                    elapsedMillis,
                    lastSeen,
                    overtakes,
                    arrived,
                    true);
        }
    }
}

package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.battle.live.BattleGuidance;
import com.a207.runkk.battle.live.BattleStart;
import com.a207.runkk.battle.live.CountdownStarted;
import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.battle.live.OpponentProgress;
import com.a207.runkk.battle.live.TrackPoint;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.RelayEntry;
import com.a207.runkk.crew.live.CrewRunCancelled;
import com.a207.runkk.crew.live.CrewRunEnded;
import com.a207.runkk.crew.live.CrewRunRelay;
import com.a207.runkk.crew.live.CrewRunStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 실시간 메시지 계약 시험(14장, 5.7.2, design-review.md H-17). 앱 저장소의 표본(contract/realtime/*.json)을 그대로 복사해 두
 * 방향을 확인한다: (1) 서버 RealtimeMessageSerializer가 battle.live·crew.live record를 앱 표본과 같은 JSON으로 낸다 (2)
 * 앱 ProgressReport JSON(inbound-progress.json)을 서버 InboundProgress로 읽는다.
 */
@org.junit.jupiter.api.DisplayName("실시간 메시지 계약(서버 직렬화 ↔ 앱 표본)")
class RealtimeContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> LOCATION_KEYS =
            Set.of("latitude", "longitude", "lat", "lng", "lon", "course", "route", "points");

    private final RealtimeMessageSerializer serializer = new RealtimeMessageSerializer();

    /** 앱 표본과 같은 값의 서버 메시지. */
    private static Map<String, BattleMessage> serverMessages() {
        DisplayName wind = DisplayName.of("바람처럼");
        return Map.of(
                "MatchFound",
                new MatchFound(
                        101,
                        wind,
                        Tier.GOLD,
                        Distance.ofMeters(5_000),
                        Duration.ofSeconds(1_625),
                        Duration.ofSeconds(1_580),
                        3),
                "BattleStart",
                new BattleStart(
                        101,
                        LocalDateTime.of(2026, 10, 20, 19, 0, 13),
                        List.of(
                                new TrackPoint(0, 0),
                                new TrackPoint(60, 190),
                                new TrackPoint(120, 385),
                                new TrackPoint(1_580, 5_000))),
                "BattleStart-random",
                new BattleStart(102, LocalDateTime.of(2026, 10, 20, 19, 5, 13, 500_000_000), null),
                "OpponentProgress",
                new OpponentProgress(
                        101,
                        wind,
                        Distance.ofMeters(1_240),
                        Duration.ofSeconds(402),
                        -35,
                        OpponentProgress.Connection.CHECKING),
                "BattleGuidance",
                new BattleGuidance(101, BattleGuidance.Kind.OVERTOOK, 12),
                "CountdownStarted",
                new CountdownStarted(101, wind, LocalDateTime.of(2026, 10, 20, 19, 27, 13)),
                "BattleEnded",
                new BattleEnded(101, BattleEnded.Status.OPPONENT_FORFEITED));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("대결 메시지 6종(표본 7개)을 앱 표본과 같은 JSON으로 직렬화한다")
    void serverMessages_matchAppSamples() throws IOException {
        Map<String, BattleMessage> messages = serverMessages();
        for (Map.Entry<String, BattleMessage> e : messages.entrySet()) {
            JsonNode expected = JSON.readTree(sample(e.getKey() + ".json"));
            JsonNode actual = JSON.readTree(serializer.serialize(e.getValue()));
            assertThat(actual).as("표본 " + e.getKey()).isEqualTo(expected);
        }
        assertThat(messages.values().stream().map(BattleMessage::type).distinct()).hasSize(6);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("앱 ProgressReport JSON을 InboundProgress로 읽는다")
    void appProgress_isReadAsInboundProgress() throws IOException {
        InboundProgress inbound =
                serializer.readInbound(sample("inbound-progress.json")).orElseThrow();
        assertThat(inbound.channel()).isEqualTo(InboundProgress.Channel.BATTLE);
        assertThat(inbound.channelId()).isEqualTo(101);
        assertThat(inbound.report().distance()).isEqualTo(Distance.ofMeters(1_275));
        assertThat(inbound.report().elapsed()).isEqualTo(Duration.ofSeconds(410));
        assertThat(inbound.report().sentAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 7, 3, 250_000_000));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("깨진 JSON·모르는 채널·음수·빠진 항목은 버린다")
    void malformedInbound_isDropped() {
        assertThat(serializer.readInbound("not json")).isEmpty();
        assertThat(
                        serializer.readInbound(
                                "{\"channel\":\"CHAT\",\"channelId\":1,\"distanceMeters\":1,"
                                    + "\"elapsedSeconds\":1,\"sentAt\":\"2026-10-20T19:07:03\"}"))
                .isEmpty();
        assertThat(
                        serializer.readInbound(
                                "{\"channel\":\"BATTLE\",\"channelId\":1,\"distanceMeters\":-1,"
                                    + "\"elapsedSeconds\":1,\"sentAt\":\"2026-10-20T19:07:03\"}"))
                .isEmpty();
        assertThat(serializer.readInbound("{\"channel\":\"BATTLE\",\"channelId\":1}")).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("어떤 서버 메시지에도 위도·경도·코스 항목이 없다(FR-089, NFR-021)")
    void noLocationFields() throws IOException {
        for (BattleMessage m : serverMessages().values()) {
            List<String> keys = keysOf(JSON.readTree(serializer.serialize(m)));
            assertThat(keys).as(m.type()).noneMatch(k -> LOCATION_KEYS.contains(k.toLowerCase()));
        }
        for (CrewRunMessage m : crewMessages().values()) {
            List<String> keys = keysOf(JSON.readTree(serializer.serialize(m)));
            assertThat(keys).as(m.type()).noneMatch(k -> LOCATION_KEYS.contains(k.toLowerCase()));
        }
    }

    /** 앱 크루런 표본과 같은 값의 서버 메시지. */
    private static Map<String, CrewRunMessage> crewMessages() {
        DisplayName bear = DisplayName.of("새벽곰");
        DisplayName fox = DisplayName.of("달리는여우");
        DisplayName rabbit = DisplayName.of("한강토끼");
        return Map.of(
                "CrewRunStart",
                new CrewRunStart(
                        77, LocalDateTime.of(2026, 10, 21, 6, 0), List.of(bear, fox, rabbit)),
                "CrewRunCancelled",
                new CrewRunCancelled(77, "참가자 부족"),
                "CrewRunRelay",
                new CrewRunRelay(
                        77,
                        List.of(
                                new RelayEntry(
                                        bear,
                                        Distance.ofMeters(3_120),
                                        1,
                                        RelayEntry.Connection.CONNECTED),
                                new RelayEntry(
                                        fox,
                                        Distance.ofMeters(2_980),
                                        2,
                                        RelayEntry.Connection.CHECKING),
                                new RelayEntry(
                                        rabbit,
                                        Distance.ofMeters(1_500),
                                        null,
                                        RelayEntry.Connection.QUIT))),
                "CrewRunEnded",
                new CrewRunEnded(77));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루런 메시지 4종을 앱 표본과 같은 JSON으로 직렬화한다")
    void crewRunMessages_matchAppSamples() throws IOException {
        Map<String, CrewRunMessage> messages = crewMessages();
        for (Map.Entry<String, CrewRunMessage> e : messages.entrySet()) {
            JsonNode expected = JSON.readTree(sample(e.getKey() + ".json"));
            JsonNode actual = JSON.readTree(serializer.serialize(e.getValue()));
            assertThat(actual).as("표본 " + e.getKey()).isEqualTo(expected);
            assertThat(e.getValue().type()).isEqualTo(e.getKey());
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("앱 크루런 진행값(channel CREW_RUN)을 InboundProgress로 읽는다")
    void appCrewRunProgress_isReadAsInboundProgress() throws IOException {
        InboundProgress inbound =
                serializer
                        .readInbound(
                                sample("inbound-progress.json")
                                        .replace("\"BATTLE\"", "\"CREW_RUN\""))
                        .orElseThrow();
        assertThat(inbound.channel()).isEqualTo(InboundProgress.Channel.CREW_RUN);
    }

    private static List<String> keysOf(JsonNode node) {
        List<String> keys = new ArrayList<>();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                keys.add(field.getKey());
                keys.addAll(keysOf(field.getValue()));
            }
        }
        if (node.isArray()) {
            node.forEach(child -> keys.addAll(keysOf(child)));
        }
        return keys;
    }

    private static String sample(String name) throws IOException {
        try (InputStream in =
                RealtimeContractTest.class.getResourceAsStream("/contract/realtime/" + name)) {
            assertThat(in).as("표본 " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

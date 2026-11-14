package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.battle.live.BattleGuidance;
import com.a207.runkk.battle.live.BattleStart;
import com.a207.runkk.battle.live.CountdownStarted;
import com.a207.runkk.battle.live.FriendInviteDeclined;
import com.a207.runkk.battle.live.FriendInviteExpired;
import com.a207.runkk.battle.live.MatchFailed;
import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.battle.live.OpponentProgress;
import com.a207.runkk.battle.live.TrackPoint;
import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.Tier;
import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.RelayEntry;
import com.a207.runkk.crew.chat.ChatMessageKind;
import com.a207.runkk.crew.chat.CrewChatSignal;
import com.a207.runkk.crew.chat.ReactionKind;
import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import com.a207.runkk.crew.chat.live.CrewChatPosted;
import com.a207.runkk.crew.chat.live.CrewChatReactionChanged;
import com.a207.runkk.crew.chat.live.CrewChatRemoved;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 실시간 메시지 계약 시험(14장, 5.7.2, design-review.md H-17). 앱 저장소의 표본(contract/realtime/*.json)을 그대로 복사해 두
 * 방향을 확인한다: (1) 서버 RealtimeMessageSerializer가 battle.live·crew.live record를 앱 표본과 같은 JSON으로 낸다 (2)
 * 앱 ProgressReport JSON(inbound-progress.json)을 서버 InboundProgress로 읽는다 (3) v0.11 채팅:
 * crew.chat.live 3종(표본 5개)과 앱 ChatPresence(inbound-chat-presence.json)(F.3.2, TC-2455).
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
                        "MALE",
                        Distance.ofMeters(5_000),
                        Duration.ofSeconds(1_625),
                        Duration.ofSeconds(1_580),
                        3,
                        null,
                        // 설계 5.31 B: 상대 모습(안 입은 부위 BOTTOM은 키가 없다)
                        new AvatarLook(
                                "MALE",
                                Map.of(ItemSlot.SHOES, 31L, ItemSlot.TOP, 12L),
                                1,
                                42,
                                "head:7:ab12")),
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
                new BattleEnded(101, BattleEnded.Status.OPPONENT_FORFEITED),
                "MatchFailed",
                new MatchFailed("MATCH_FAILED", "대결을 만들지 못했어요. 다시 시도해 주세요"),
                // v0.14 친구 대결 초대(D-221·D-223, TC-9853): 초대한 앱에만 가는 inviteId 하나
                "FriendInviteDeclined",
                new FriendInviteDeclined(41),
                "FriendInviteExpired",
                new FriendInviteExpired(42));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "대결 메시지 9종(표본 10개, v0.14 친구 대결 초대 2종 포함)을 앱 표본과 같은 JSON으로 직렬화한다")
    void serverMessages_matchAppSamples() throws IOException {
        Map<String, BattleMessage> messages = serverMessages();
        for (Map.Entry<String, BattleMessage> e : messages.entrySet()) {
            JsonNode expected = JSON.readTree(sample(e.getKey() + ".json"));
            JsonNode actual = JSON.readTree(serializer.serialize(e.getValue()));
            assertThat(actual).as("표본 " + e.getKey()).isEqualTo(expected);
        }
        assertThat(messages.values().stream().map(BattleMessage::type).distinct()).hasSize(9);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5783 배치고사 MatchFound는 placementRound를 싣고 상대 티어는 UNRANKED다(표본 MatchFound-placement)."
                    + " 배치고사가 아니면 placementRound를 싣지 않아 옛 표본과 같다(05-03 5.7.3.4)")
    void placementMatchFound_matchesSample() throws IOException {
        MatchFound placement =
                new MatchFound(
                        103,
                        DisplayName.of("느긋한고래"),
                        Tier.UNRANKED,
                        "FEMALE",
                        Distance.ofMeters(5_000),
                        null,
                        Duration.ofSeconds(2_010),
                        3,
                        1);
        assertThat(JSON.readTree(serializer.serialize(placement)))
                .isEqualTo(JSON.readTree(sample("MatchFound-placement.json")));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("앱 ProgressReport JSON을 InboundProgress로 읽는다")
    void appProgress_isReadAsInboundProgress() throws IOException {
        InboundProgress inbound =
                (InboundProgress)
                        serializer.readInbound(sample("inbound-progress.json")).orElseThrow();
        assertThat(inbound.channel()).isEqualTo(InboundProgress.Channel.BATTLE);
        assertThat(inbound.channelId()).isEqualTo(101);
        assertThat(inbound.report().distance()).isEqualTo(Distance.ofMeters(1_275));
        assertThat(inbound.report().elapsed()).isEqualTo(Duration.ofSeconds(410));
        assertThat(inbound.report().sentAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 7, 3, 250_000_000));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "진행값 sentAt: 오프셋 있는 ISO(+09:00, Z)는 서버 시간대로 바꾸고, 오프셋 없는 옛 형식도 읽는다(TC-6465)")
    void progressSentAt_acceptsOffsetAndLocal() throws IOException {
        InboundProgress offset =
                (InboundProgress)
                        serializer
                                .readInbound(sample("inbound-progress-offset.json"))
                                .orElseThrow();
        assertThat(offset.channelId()).isEqualTo(7);
        assertThat(offset.report().distance()).isEqualTo(Distance.ofMeters(35));
        assertThat(offset.report().sentAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0, 11));

        InboundProgress local =
                (InboundProgress)
                        serializer.readInbound(sample("inbound-progress.json")).orElseThrow();
        assertThat(local.report().sentAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 19, 7, 3, 250_000_000));

        assertThat(sentAtOf("2026-10-22T10:00:11.500Z"))
                .isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0, 11, 500_000_000));
        assertThat(sentAtOf("2026-10-22T19:00:11.500+09:00"))
                .isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0, 11, 500_000_000));
        assertThat(sentAtOf("2026-10-22T12:00:11+02:00"))
                .isEqualTo(LocalDateTime.of(2026, 10, 22, 19, 0, 11));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("진행값 sentAt이 ISO 날짜·시각이 아니거나 시간대 ID만 있으면 버린다")
    void progressSentAt_malformedIsDropped() {
        for (String bad :
                List.of(
                        "2026-10-22 19:00:11",
                        "2026-10-22",
                        "19:00:11",
                        "2026-10-22T19:00:11+25:00",
                        "",
                        "2026-10-22T19:00:11+09:00[Asia/Seoul]")) {
            assertThat(serializer.readInbound(progressWithSentAt(bad))).as(bad).isEmpty();
        }
    }

    private LocalDateTime sentAtOf(String sentAt) {
        return ((InboundProgress) serializer.readInbound(progressWithSentAt(sentAt)).orElseThrow())
                .report()
                .sentAt();
    }

    private static String progressWithSentAt(String sentAt) {
        return "{\"channel\":\"BATTLE\",\"channelId\":7,\"distanceMeters\":35,"
                + "\"elapsedSeconds\":11,\"sentAt\":\""
                + sentAt
                + "\"}";
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
        for (CrewChatSignal m : chatMessages().values()) {
            List<String> keys = keysOf(JSON.readTree(serializer.serialize(m)));
            assertThat(keys).as(m.type()).noneMatch(k -> LOCATION_KEYS.contains(k.toLowerCase()));
        }
        assertThat(keysOf(JSON.readTree(sample("inbound-chat-presence.json"))))
                .noneMatch(k -> LOCATION_KEYS.contains(k.toLowerCase()));
    }

    /** 앱 채팅 표본과 같은 값의 서버 메시지(crew.chat.live, F.3.2). */
    private static Map<String, CrewChatSignal> chatMessages() {
        ZoneOffset seoul = ZoneOffset.ofHours(9);
        Map<ReactionKind, Integer> reactions = new LinkedHashMap<>();
        reactions.put(ReactionKind.LIKE, 2);
        reactions.put(ReactionKind.HEART, 1);
        reactions.put(ReactionKind.CHECK, 3);
        return Map.of(
                "CrewChatPosted",
                new CrewChatPosted(
                        7,
                        801,
                        3L,
                        "새벽곰",
                        "내일 7시 출발 🏃",
                        OffsetDateTime.of(2026, 10, 22, 19, 7, 3, 0, seoul),
                        "0f8fad5b-d9cb-469f-a165-70867728950e",
                        ChatMessageKind.TEXT,
                        "LEADER",
                        null,
                        "head:3:ab12",
                        "MALE"),
                "CrewChatPosted-withdrawn",
                new CrewChatPosted(
                        7,
                        802,
                        null,
                        "탈퇴한 사용자",
                        "먼저 갈게요",
                        OffsetDateTime.of(2026, 10, 22, 19, 7, 3, 500_000_000, seoul),
                        "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                        ChatMessageKind.TEXT,
                        null,
                        null,
                        null,
                        null),
                // W5 러닝 완료 시스템 메시지(S15P31A207-296, C7): 닉네임·거리만
                "CrewChatPosted-runFinished",
                new CrewChatPosted(
                        7,
                        803,
                        4L,
                        "느긋한고래",
                        "느긋한고래님이 5.02km를 달렸어요",
                        OffsetDateTime.of(2026, 10, 22, 19, 40, 0, 0, seoul),
                        "9b2f4c3e-6a1d-3c5b-8e7f-0a1b2c3d4e5f",
                        ChatMessageKind.RUN_FINISHED,
                        "MEMBER",
                        5_021L,
                        null,
                        "FEMALE"),
                "CrewChatRemoved",
                new CrewChatRemoved(7, 801, ChatRemovalReason.DELETED),
                "CrewChatRemoved-hidden",
                new CrewChatRemoved(7, 801, ChatRemovalReason.HIDDEN),
                "CrewChatReactionChanged",
                new CrewChatReactionChanged(7, 801, reactions));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "채팅 메시지 3종(표본 6개, W5 러닝 완료 포함)을 앱 표본과 같은 JSON으로 직렬화한다(sentAt +09:00, 탈퇴 작성자 null, 사유 상수"
                    + " 이름)")
    void chatMessages_matchAppSamples() throws IOException {
        for (Map.Entry<String, CrewChatSignal> e : chatMessages().entrySet()) {
            JsonNode expected = JSON.readTree(sample(e.getKey() + ".json"));
            JsonNode actual = JSON.readTree(serializer.serialize(e.getValue()));
            assertThat(actual).as("표본 " + e.getKey()).isEqualTo(expected);
            assertThat(e.getKey()).startsWith(e.getValue().type());
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "앱 ChatPresence JSON을 InboundChatPresence로 읽고, 진행값은 type Progress여도 읽는다")
    void appChatPresence_isReadAsInboundChatPresence() throws IOException {
        assertThat(serializer.readInbound(sample("inbound-chat-presence.json")))
                .contains(new InboundChatPresence(7, true));
        assertThat(
                        serializer.readInbound(
                                "{\"type\":\"ChatPresence\",\"crewId\":7,\"present\":false}"))
                .contains(new InboundChatPresence(7, false));
        String typedProgress =
                sample("inbound-progress.json").replaceFirst("\\{", "{\"type\":\"Progress\",");
        assertThat(serializer.readInbound(typedProgress)).get().isInstanceOf(InboundProgress.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "모르는 type, present가 참·거짓이 아니거나 crewId가 없거나 음수인 ChatPresence는 버린다")
    void malformedChatPresence_isDropped() {
        assertThat(serializer.readInbound("{\"type\":\"Typing\",\"crewId\":7}")).isEmpty();
        assertThat(
                        serializer.readInbound(
                                "{\"type\":\"ChatPresence\",\"crewId\":7,\"present\":\"yes\"}"))
                .isEmpty();
        assertThat(serializer.readInbound("{\"type\":\"ChatPresence\",\"present\":true}"))
                .isEmpty();
        assertThat(
                        serializer.readInbound(
                                "{\"type\":\"ChatPresence\",\"crewId\":-1,\"present\":true}"))
                .isEmpty();
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
                relay().forRecipient(11),
                "CrewRunEnded",
                new CrewRunEnded(77));
    }

    /** 받는 사람별로 mine을 정하기 전의 중계(표본 값). */
    private static CrewRunRelay relay() {
        DisplayName bear = DisplayName.of("새벽곰");
        DisplayName fox = DisplayName.of("달리는여우");
        DisplayName rabbit = DisplayName.of("한강토끼");
        return new CrewRunRelay(
                77,
                Distance.ofMeters(7_600),
                List.of(
                        new RelayEntry(
                                bear,
                                Distance.ofMeters(3_120),
                                RelayEntry.State.RUNNING,
                                11,
                                false),
                        new RelayEntry(
                                fox, Distance.ofMeters(2_980), RelayEntry.State.PAUSED, 12, false),
                        new RelayEntry(
                                rabbit,
                                Distance.ofMeters(1_500),
                                RelayEntry.State.ENDED,
                                13,
                                false)));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "크루런 중계 mine(D-161): 받는 사람 줄만 true, 나머지 false, memberId는 그대로, 참가자 아닌 사람은 모두 false")
    void crewRunRelay_mineIsPerRecipient() throws IOException {
        for (long recipient : List.of(11L, 12L, 13L)) {
            JsonNode entries =
                    JSON.readTree(serializer.serialize(relay().forRecipient(recipient)))
                            .path("entries");
            assertThat(entries).hasSize(3);
            for (JsonNode entry : entries) {
                assertThat(entry.path("mine").isBoolean()).isTrue();
                assertThat(entry.path("mine").asBoolean())
                        .as("받는 사람 " + recipient + ", 줄 " + entry.path("memberId").asLong())
                        .isEqualTo(entry.path("memberId").asLong() == recipient);
            }
        }
        JsonNode outsider =
                JSON.readTree(serializer.serialize(relay().forRecipient(99))).path("entries");
        outsider.forEach(entry -> assertThat(entry.path("mine").asBoolean()).isFalse());
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
                (InboundProgress)
                        serializer
                                .readInbound(
                                        sample("inbound-progress.json")
                                                .replace("\"BATTLE\"", "\"CREW_RUN\""))
                                .orElseThrow();
        assertThat(inbound.channel()).isEqualTo(InboundProgress.Channel.CREW_RUN);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "닫기 코드 계약(5.7.2, F.3.3): 4401 TOKEN_EXPIRED는 대결·크루런·채팅이 함께 쓰는 연결의 토큰 만료, 1000·1008·1012"
                    + " 그대로")
    void closeCodes_matchContract() {
        assertThat(SocketSessionRegistry.TOKEN_EXPIRED.getCode()).isEqualTo(4401);
        assertThat(SocketSessionRegistry.TOKEN_EXPIRED.getReason()).isEqualTo("TOKEN_EXPIRED");
        assertThat(org.springframework.web.socket.CloseStatus.NORMAL.getCode()).isEqualTo(1000);
        assertThat(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION.getCode())
                .isEqualTo(1008);
        assertThat(org.springframework.web.socket.CloseStatus.SERVICE_RESTARTED.getCode())
                .isEqualTo(1012);
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

package com.a207.runkk.realtime;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.ServerZone;
import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.chat.CrewChatSignal;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 서버 → 앱 메시지를 JSON으로 바꾸고 앱 → 서버 진행값을 읽는다(설계 6.1.13, 5.7.2, D-06). type() 값을 JSON의 type 필드로 쓰고 나머지
 * record 구성요소를 그대로 직렬화하므로 메시지 클래스 목록을 모른다. 형식: 거리는 미터(정수), 경과·기록 시간은 초(정수), 시각은 서버 시간대(한국)의
 * ISO-8601 LocalDateTime(오프셋 없음, 밀리초는 있을 때만), 채팅의 OffsetDateTime은 같은 꼴에 +09:00 오프셋을 붙인다(F.3.2), 앱 →
 * 서버 진행값 sentAt은 오프셋이 있으면 서버 시간대로 바꾸고 없으면 그대로 읽는다(TC-6465), 표시명은 문자열. 앱 → 서버는 type으로 진행값(Progress
 * 또는 type 없음)과 채팅방 입장(ChatPresence)을 나눈다(D-103). 앱 해독기와 계약 시험으로 맞춘다(H-17).
 */
@Component
public class RealtimeMessageSerializer {

    static final String TYPE = "type";

    /** 앱 → 서버 진행값 type(없어도 진행값으로 읽는다, 옛 앱 호환). */
    static final String PROGRESS = "Progress";

    /** 앱 → 서버 채팅방 입장 type. */
    static final String CHAT_PRESENCE = "ChatPresence";

    private static final Logger log = LoggerFactory.getLogger(RealtimeMessageSerializer.class);

    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter SECONDS_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final DateTimeFormatter MILLIS_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");
    private static final long MILLIS_PER_SECOND = 1_000;

    /** 서버 시간대(RunkkApplication의 Clock과 같다). 오프셋 있는 진행값 시각을 이 시간대로 바꾼다. */
    static final ZoneId SERVER_ZONE = ServerZone.ID;

    /** 진행값 sentAt: ISO 날짜·시각 + 선택 오프셋(+09:00, Z). */
    private static final DateTimeFormatter SENT_AT =
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .optionalStart()
                    .appendOffsetId()
                    .optionalEnd()
                    .toFormatter()
                    .withResolverStyle(ResolverStyle.STRICT)
                    .withChronology(IsoChronology.INSTANCE);

    /** 진행값 거리 상한(풀코스의 두 배를 넘는 대결 거리는 없다). */
    static final long MAX_METERS = 100_000;

    /** 진행값 경과 시간 상한(하루). */
    static final long MAX_SECONDS = 24 * 60 * 60;

    private final ObjectMapper mapper;

    public RealtimeMessageSerializer() {
        SimpleModule formats = new SimpleModule("realtime-formats");
        formats.addSerializer(Distance.class, new DistanceSerializer());
        formats.addSerializer(Duration.class, new SecondsSerializer());
        formats.addSerializer(DisplayName.class, new DisplayNameSerializer());
        formats.addSerializer(LocalDateTime.class, new LocalTimeSerializer());
        formats.addSerializer(OffsetDateTime.class, new OffsetTimeSerializer());
        this.mapper = JsonMapper.builder().addModule(formats).build();
    }

    /** 대결 메시지 → {"type": type(), …구성요소}. */
    public String serialize(BattleMessage m) {
        return serialize(m.type(), m);
    }

    /** 크루런 메시지 → {"type": type(), …구성요소}. */
    public String serialize(CrewRunMessage m) {
        return serialize(m.type(), m);
    }

    /** 채팅 메시지 → {"type": type(), …구성요소}(crew.chat.live, F.3.2). */
    public String serialize(CrewChatSignal m) {
        return serialize(m.type(), m);
    }

    private String serialize(String type, Object message) {
        ObjectNode node = mapper.createObjectNode();
        node.put(TYPE, type);
        node.setAll((ObjectNode) mapper.valueToTree(message));
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("실시간 메시지를 직렬화하지 못했다: " + type, e);
        }
    }

    /**
     * 앱 → 서버 메시지 JSON → InboundMessage. type이 "Progress"이거나 없으면 진행값(channel, channelId,
     * distanceMeters, elapsedSeconds, sentAt), "ChatPresence"면 채팅방 입장(crewId, present). 형식이 틀리거나
     * 모르는 type이면 비어 있다(버린다).
     */
    public Optional<InboundMessage> readInbound(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            JsonNode type = node.path(TYPE);
            if (type.isMissingNode() || PROGRESS.equals(type.asText())) {
                return Optional.of(readProgress(node));
            }
            if (CHAT_PRESENCE.equals(type.asText())) {
                return Optional.of(readChatPresence(node));
            }
            log.info("socket_message_dropped reason=unknown_type");
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static InboundProgress readProgress(JsonNode node) {
        InboundProgress.Channel channel =
                InboundProgress.Channel.valueOf(node.path("channel").asText());
        long channelId = requirePositive(node.path("channelId"));
        long meters = requireWithin(node.path("distanceMeters"), MAX_METERS);
        long seconds = requireWithin(node.path("elapsedSeconds"), MAX_SECONDS);
        LocalDateTime sentAt = readSentAt(node.path("sentAt").asText());
        ProgressReport report =
                new ProgressReport(Distance.ofMeters(meters), Duration.ofSeconds(seconds), sentAt);
        return new InboundProgress(channel, channelId, report);
    }

    /**
     * 진행값 sentAt: 오프셋 있는 ISO-8601(앱 v0.11, "2026-10-22T19:00:11+09:00", "Z")은 서버 시간대의
     * LocalDateTime으로 바꾸고, 오프셋 없는 옛 형식("2026-10-22T19:00:11.250")은 서버 시간대 시각으로 그대로 읽는다(TC-6465).
     * 시간대 ID 꼬리([Asia/Seoul])는 받지 않는다.
     */
    static LocalDateTime readSentAt(String text) {
        TemporalAccessor parsed =
                SENT_AT.parseBest(text, OffsetDateTime::from, LocalDateTime::from);
        if (parsed instanceof OffsetDateTime offset) {
            return offset.atZoneSameInstant(SERVER_ZONE).toLocalDateTime();
        }
        return (LocalDateTime) parsed;
    }

    private static InboundChatPresence readChatPresence(JsonNode node) {
        JsonNode present = node.path("present");
        if (!present.isBoolean()) {
            throw new IllegalArgumentException("present가 참·거짓이 아니다");
        }
        return new InboundChatPresence(requirePositive(node.path("crewId")), present.asBoolean());
    }

    private static long requirePositive(JsonNode node) {
        if (!node.canConvertToLong() || node.asLong() <= 0) {
            throw new IllegalArgumentException("양의 정수가 아니다");
        }
        return node.asLong();
    }

    /** 0 이상 상한 이하의 정수. 터무니없는 값(시간 넘침)을 들이지 않는다. */
    private static long requireWithin(JsonNode node, long max) {
        if (!node.canConvertToLong() || node.asLong() < 0 || node.asLong() > max) {
            throw new IllegalArgumentException("범위 밖의 값");
        }
        return node.asLong();
    }

    private static final class DistanceSerializer extends JsonSerializer<Distance> {
        @Override
        public void serialize(Distance value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeNumber(value.meters());
        }
    }

    /** 초 반올림. */
    private static final class SecondsSerializer extends JsonSerializer<Duration> {
        @Override
        public void serialize(Duration value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeNumber(Math.round(value.toMillis() / (double) MILLIS_PER_SECOND));
        }
    }

    private static final class DisplayNameSerializer extends JsonSerializer<DisplayName> {
        @Override
        public void serialize(DisplayName value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.value());
        }
    }

    /** 초 아래가 0이면 초까지, 아니면 밀리초까지. */
    private static final class LocalTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(
                LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.getNano() == 0 ? SECONDS.format(value) : MILLIS.format(value));
        }
    }

    /** 초 아래가 0이면 초까지, 아니면 밀리초까지, 끝에 오프셋(+09:00). */
    private static final class OffsetTimeSerializer extends JsonSerializer<OffsetDateTime> {
        @Override
        public void serialize(
                OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(
                    value.getNano() == 0
                            ? SECONDS_OFFSET.format(value)
                            : MILLIS_OFFSET.format(value));
        }
    }
}

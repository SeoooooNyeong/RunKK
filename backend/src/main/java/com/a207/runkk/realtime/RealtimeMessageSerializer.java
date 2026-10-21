package com.a207.runkk.realtime;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
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
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 서버 → 앱 메시지를 JSON으로 바꾸고 앱 → 서버 진행값을 읽는다(설계 6.1.13, 5.7.2, D-06). type() 값을 JSON의 type 필드로 쓰고 나머지
 * record 구성요소를 그대로 직렬화하므로 메시지 클래스 목록을 모른다. 형식: 거리는 미터(정수), 경과·기록 시간은 초(정수), 시각은 서버 시간대(한국)의
 * ISO-8601 LocalDateTime(오프셋 없음, 밀리초는 있을 때만), 표시명은 문자열. 앱 해독기와 계약 시험으로 맞춘다(H-17).
 */
@Component
public class RealtimeMessageSerializer {

    static final String TYPE = "type";

    private static final DateTimeFormatter SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final long MILLIS_PER_SECOND = 1_000;

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
        this.mapper = JsonMapper.builder().addModule(formats).build();
    }

    /** 대결 메시지 → {"type": type(), …구성요소}. */
    public String serialize(BattleMessage m) {
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
     * 앱 진행값 JSON(channel, channelId, distanceMeters, elapsedSeconds, sentAt) → InboundProgress. 형식이
     * 틀리면 비어 있다(버린다).
     */
    public Optional<InboundProgress> readInbound(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            InboundProgress.Channel channel =
                    InboundProgress.Channel.valueOf(node.path("channel").asText());
            long channelId = requirePositive(node.path("channelId"));
            long meters = requireWithin(node.path("distanceMeters"), MAX_METERS);
            long seconds = requireWithin(node.path("elapsedSeconds"), MAX_SECONDS);
            LocalDateTime sentAt = LocalDateTime.parse(node.path("sentAt").asText());
            ProgressReport report =
                    new ProgressReport(
                            Distance.ofMeters(meters), Duration.ofSeconds(seconds), sentAt);
            return Optional.of(new InboundProgress(channel, channelId, report));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
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
}

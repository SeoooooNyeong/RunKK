package com.a207.runkk.infra.integration.weather;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.running.SkyCondition;
import com.a207.runkk.running.Weather;
import com.a207.runkk.running.WeatherGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 기상청 초단기예보(공공데이터포털 VilageFcstInfoService_2.0/getUltraSrtFcst)로 러닝 시작 무렵의 기온·하늘 상태를 조회한다(설계 6.1.13,
 * FR-184, C-020). 받은 좌표(소수 둘째 자리, NFR-038)를 기상청 격자로 바꿔 보낸다. 3초 제한이고 실패·키 없음·오래된 시각이면 비어 있다(다시 요청하지
 * 않음).
 */
@Component
public class KmaWeatherGateway implements WeatherGateway {

    private static final Logger log = LoggerFactory.getLogger(KmaWeatherGateway.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    /** 초단기예보는 매시 30분에 만들고 45분쯤 공개한다. */
    private static final int PUBLISH_DELAY_MINUTES = 45;

    private static final int BASE_MINUTE = 30;
    private static final Set<String> RAIN = Set.of("1", "2", "5", "6");
    private static final Set<String> SNOW = Set.of("3", "7");

    private final RestClient client;
    private final String serviceKey;

    @Autowired
    public KmaWeatherGateway(
            RestClient.Builder builder,
            @Value("${runkk.weather.kma.base-url}") String baseUrl,
            @Value("${runkk.weather.kma.service-key:}") String serviceKey) {
        this(builder.requestFactory(timeouts()).baseUrl(baseUrl).build(), serviceKey);
    }

    KmaWeatherGateway(RestClient client, String serviceKey) {
        this.client = client;
        this.serviceKey = serviceKey == null ? "" : serviceKey.strip();
    }

    @Override
    public Optional<Weather> lookup(GeoPoint coarsePoint, LocalDateTime at) {
        if (serviceKey.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime base = baseTimeFor(at);
        int[] grid = toGrid(coarsePoint.latitude(), coarsePoint.longitude());
        JsonNode body;
        try {
            body =
                    client.get()
                            .uri(
                                    b ->
                                            b.path("/getUltraSrtFcst")
                                                    .queryParam("serviceKey", serviceKey)
                                                    .queryParam("pageNo", 1)
                                                    .queryParam("numOfRows", 60)
                                                    .queryParam("dataType", "JSON")
                                                    .queryParam("base_date", base.format(DATE))
                                                    .queryParam("base_time", base.format(TIME))
                                                    .queryParam("nx", grid[0])
                                                    .queryParam("ny", grid[1])
                                                    .build())
                            .retrieve()
                            .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("weather_lookup_failed error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
        return parse(body, at);
    }

    /** at에 쓸 예보의 발표 시각(45분 전 이전의 가장 최근 HH:30). */
    static LocalDateTime baseTimeFor(LocalDateTime at) {
        LocalDateTime published = at.minusMinutes(PUBLISH_DELAY_MINUTES);
        LocalDateTime base = published.truncatedTo(ChronoUnit.HOURS).withMinute(BASE_MINUTE);
        return base.isAfter(published) ? base.minusHours(1) : base;
    }

    /** 예보 중 at이 속한 시각(정시)의 기온·하늘 상태. */
    static Optional<Weather> parse(JsonNode body, LocalDateTime at) {
        if (body == null || !"00".equals(body.at("/response/header/resultCode").asText())) {
            return Optional.empty();
        }
        String slot =
                at.truncatedTo(ChronoUnit.HOURS).format(DATE)
                        + at.truncatedTo(ChronoUnit.HOURS).format(TIME);
        Map<String, Map<String, String>> bySlot = new HashMap<>();
        for (JsonNode item : body.at("/response/body/items/item")) {
            String key = item.path("fcstDate").asText() + item.path("fcstTime").asText();
            bySlot.computeIfAbsent(key, k -> new HashMap<>())
                    .put(item.path("category").asText(), item.path("fcstValue").asText());
        }
        Map<String, String> values = bySlot.get(slot);
        if (values == null) {
            values = bySlot.keySet().stream().sorted().findFirst().map(bySlot::get).orElse(null);
        }
        if (values == null || !values.containsKey("T1H") || !values.containsKey("SKY")) {
            return Optional.empty();
        }
        try {
            int temperature = (int) Math.round(Double.parseDouble(values.get("T1H")));
            return Optional.of(new Weather(temperature, sky(values.get("SKY"), values.get("PTY"))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static SkyCondition sky(String sky, String precipitation) {
        if (precipitation != null && RAIN.contains(precipitation)) {
            return SkyCondition.RAIN;
        }
        if (precipitation != null && SNOW.contains(precipitation)) {
            return SkyCondition.SNOW;
        }
        return switch (sky) {
            case "1" -> SkyCondition.CLEAR;
            case "3" -> SkyCondition.CLOUDY;
            case "4" -> SkyCondition.OVERCAST;
            default -> throw new IllegalArgumentException("하늘 상태 코드: " + sky);
        };
    }

    /** 위경도 → 기상청 동네예보 격자(람베르트 정각원추 투영, 기상청 제공 변환식). */
    static int[] toGrid(double lat, double lon) {
        double re = 6371.00877 / 5.0;
        double degrad = Math.PI / 180.0;
        double slat1 = 30.0 * degrad;
        double slat2 = 60.0 * degrad;
        double olon = 126.0 * degrad;
        double olat = 38.0 * degrad;
        double sn =
                Math.log(Math.cos(slat1) / Math.cos(slat2))
                        / Math.log(
                                Math.tan(Math.PI * 0.25 + slat2 * 0.5)
                                        / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);
        double ra = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + lat * degrad * 0.5), sn);
        double theta = lon * degrad - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;
        int x = (int) Math.floor(ra * Math.sin(theta) + 43 + 0.5);
        int y = (int) Math.floor(ro - ra * Math.cos(theta) + 136 + 0.5);
        return new int[] {x, y};
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}

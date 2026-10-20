package com.a207.runkk.infra.integration.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.running.SkyCondition;
import com.a207.runkk.running.Weather;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** KmaWeatherGateway(FR-184, NFR-038): 격자 변환, 발표 시각, 응답 해석, 실패·키 없음은 비어 있음. */
class KmaWeatherGatewayTest {

    private static final GeoPoint SEOUL = new GeoPoint(37.57, 126.98);
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 20, 7, 10);

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://kma");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private static String body(String pty, String sky, String temperature) {
        return """
        {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
          {"category":"T1H","fcstDate":"20261020","fcstTime":"0700","fcstValue":"%s"},
          {"category":"SKY","fcstDate":"20261020","fcstTime":"0700","fcstValue":"%s"},
          {"category":"PTY","fcstDate":"20261020","fcstTime":"0700","fcstValue":"%s"},
          {"category":"T1H","fcstDate":"20261020","fcstTime":"0800","fcstValue":"20"},
          {"category":"SKY","fcstDate":"20261020","fcstTime":"0800","fcstValue":"1"}
        ]}}}}
        """
                .formatted(temperature, sky, pty);
    }

    @Test
    void grid_matchesKmaSeoulCityHall() {
        assertThat(KmaWeatherGateway.toGrid(37.5665, 126.9780)).containsExactly(60, 127);
    }

    @Test
    void baseTime_isLatestPublishedHalfHour() {
        assertThat(KmaWeatherGateway.baseTimeFor(AT))
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 5, 30));
        assertThat(KmaWeatherGateway.baseTimeFor(LocalDateTime.of(2026, 10, 20, 7, 20)))
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 6, 30));
    }

    @Test
    void lookup_parsesTemperatureAndSky() {
        server.expect(requestTo(startsWith("https://kma/getUltraSrtFcst")))
                .andExpect(queryParam("nx", "60"))
                .andExpect(queryParam("ny", "127"))
                .andExpect(queryParam("base_time", "0530"))
                .andRespond(withSuccess(body("0", "3", "11.6"), MediaType.APPLICATION_JSON));
        assertThat(new KmaWeatherGateway(builder.build(), "key").lookup(SEOUL, AT))
                .contains(new Weather(12, SkyCondition.CLOUDY));
        server.verify();
    }

    @Test
    void lookup_precipitationWinsOverSky() {
        server.expect(requestTo(startsWith("https://kma/")))
                .andRespond(withSuccess(body("1", "1", "5"), MediaType.APPLICATION_JSON));
        assertThat(new KmaWeatherGateway(builder.build(), "key").lookup(SEOUL, AT))
                .contains(new Weather(5, SkyCondition.RAIN));
        server.reset();
        server.expect(requestTo(startsWith("https://kma/")))
                .andRespond(withSuccess(body("3", "4", "-2"), MediaType.APPLICATION_JSON));
        assertThat(new KmaWeatherGateway(builder.build(), "key").lookup(SEOUL, AT))
                .contains(new Weather(-2, SkyCondition.SNOW));
    }

    @Test
    void lookup_failuresAreEmpty() {
        assertThat(new KmaWeatherGateway(builder.build(), "").lookup(SEOUL, AT)).isEmpty();

        server.expect(requestTo(startsWith("https://kma/"))).andRespond(withServerError());
        assertThat(new KmaWeatherGateway(builder.build(), "key").lookup(SEOUL, AT)).isEmpty();

        server.reset();
        server.expect(requestTo(startsWith("https://kma/")))
                .andRespond(
                        withSuccess(
                                "{\"response\":{\"header\":{\"resultCode\":\"03\"}}}",
                                MediaType.APPLICATION_JSON));
        assertThat(new KmaWeatherGateway(builder.build(), "key").lookup(SEOUL, AT)).isEmpty();

        server.reset();
        server.expect(requestTo(startsWith("https://kma/")))
                .andRespond(withSuccess(body("0", "9", "1"), MediaType.APPLICATION_JSON));
        assertThat(new KmaWeatherGateway(builder.build(), "key").lookup(SEOUL, AT)).isEmpty();
    }
}

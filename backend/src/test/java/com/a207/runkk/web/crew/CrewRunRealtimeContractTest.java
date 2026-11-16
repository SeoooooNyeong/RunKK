package com.a207.runkk.web.crew;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.CrewRunPhase;
import com.a207.runkk.crew.CrewRunStatus;
import com.a207.runkk.crew.LobbyView;
import com.a207.runkk.crew.ParticipationView;
import com.a207.runkk.crew.Readiness;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 크루런 실시간 반영 REST 계약 표본(설계 05-32 5.2, S15P31A207-306). 서버 ObjectMapper로 응답 record를 직렬화해
 * src/test/resources/contract/crew/의 표본과 항목 이름·형식·값이 같은지 본다. 표본은 앱 파싱 시험이 함께 쓴다. 표본 상황: T=19:00:00에
 * 후보 [11, 12]를 고정했고 12가 준비를 취소해 후보는 [11], 13은 입장만 했다(05-32 5.3과 같은 값).
 */
@org.junit.jupiter.api.DisplayName("크루런 실시간 반영 대기실·중간 참여 응답 계약 표본(05-32)")
class CrewRunRealtimeContractTest extends WebTestSupport {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 16, 19, 0);

    private JsonNode sample(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contract/crew/" + name)) {
            assertThat(in).as(name).isNotNull();
            return json.readTree(in);
        }
    }

    private JsonNode serialize(Object response) throws IOException {
        return json.readTree(json.writeValueAsString(response));
    }

    private static List<LobbyView.Entry> entries() {
        return List.of(
                new LobbyView.Entry(11, DisplayName.of("러너가"), Readiness.READY, "MALE"),
                new LobbyView.Entry(12, DisplayName.of("러너나"), Readiness.ENTERING, "FEMALE"),
                new LobbyView.Entry(13, DisplayName.of("러너다"), Readiness.ENTERING, "FEMALE"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "GET /crew-runs/{id}/lobby 카운트다운 중: 기존 항목 그대로 +"
                    + " crewId·revision·phase·countdownToken·startAt·후보·단추 여부")
    void lobbyCountdown_matchesSample() throws IOException {
        LobbyView view =
                new LobbyView(
                        101,
                        7,
                        T.plusMinutes(10),
                        StandardDistance.KM5,
                        Duration.ofMinutes(10),
                        CrewRunStatus.LOBBY_OPEN,
                        CrewRunPhase.COUNTDOWN,
                        19,
                        "101-18",
                        T.plusSeconds(3),
                        List.of(11L),
                        false,
                        false,
                        false,
                        null,
                        entries());
        assertThat(
                        serialize(
                                CrewDtos.LobbyResponse.of(
                                        view, 11, Map.of(11L, "default:FOX"), Map.of())))
                .isEqualTo(sample("LobbyResponse-countdown.json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "GET /crew-runs/{id}/lobby 출발 뒤 진행 참가자 본인: meRunner·measurementRecordId, 미준비 입장자 13은"
                    + " canJoin")
    void lobbyRunning_matchesSample() throws IOException {
        LobbyView mine =
                new LobbyView(
                        101,
                        7,
                        T.plusMinutes(10),
                        StandardDistance.KM5,
                        Duration.ofMinutes(9).plusSeconds(57),
                        CrewRunStatus.RUNNING,
                        CrewRunPhase.RUNNING,
                        20,
                        "101-18",
                        T.plusSeconds(3),
                        List.of(11L),
                        false,
                        false,
                        true,
                        "6f1c2a0e-3b7d-4c55-9a8e-2f4b1d0c9e71",
                        entries());
        assertThat(serialize(CrewDtos.LobbyResponse.of(mine, 11, Map.of(), Map.of())))
                .isEqualTo(sample("LobbyResponse-running.json"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "POST /crew-runs/{id}/join: 본인 joinedAt·measurementRecordId·alreadyJoined, 진행 참가자 공개"
                    + " 줄(다른 사람 기록 ID 없음)")
    void participation_matchesSample() throws IOException {
        LocalDateTime joinedAt = T.plusMinutes(5).plusSeconds(12).plusNanos(345_000_000);
        ParticipationView view =
                new ParticipationView(
                        101,
                        7,
                        22,
                        joinedAt,
                        "0b7e9c55-1d2f-4a6b-8c3e-5f9a7d1e2c40",
                        false,
                        List.of(
                                new ParticipationView.Runner(
                                        11, DisplayName.of("러너가"), T.plusSeconds(3), false),
                                new ParticipationView.Runner(
                                        13, DisplayName.of("러너다"), joinedAt, false)));
        assertThat(serialize(CrewDtos.ParticipationResponse.of(view, 13)))
                .isEqualTo(sample("ParticipationResponse.json"));
    }
}

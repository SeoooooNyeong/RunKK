package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OpponentTrack 계약 시험(14장, 설계 6.1.6, A-08). 앱 domain.battle.OpponentTrack 시험과 같은
 * 표(contract/opponent-track.csv)를 같은 규칙으로 해석해 서버 구현이 같은 출력을 내는지 확인한다. 표는 앱 저장소에서 그대로 복사한 것이고 한쪽만
 * 고치지 않는다.
 */
@DisplayName("OpponentTrack 앱·서버 계약 표")
class OpponentTrackContractTest {

    private static final String CSV = "/contract/opponent-track.csv";
    private static final long MILLIS_PER_SECOND = 1_000;

    @Test
    @DisplayName("계약 표의 모든 명령이 앱과 같은 결과를 낸다")
    void everyLineOfTheContractTable_matches() throws IOException {
        List<String> failures = new ArrayList<>();
        OpponentTrack track = OpponentTrack.empty();
        int checks = 0;
        for (String raw : lines()) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split(",");
            switch (cells[0]) {
                case "track" -> track = parseTrack(cells[1]);
                case "uniform" -> track = parseUniform(cells[1]);
                case "empty" -> track = OpponentTrack.empty();
                case "cut" -> track = track.cut(Distance.ofMeters(Long.parseLong(cells[1])));
                case "distanceAt" -> {
                    checks++;
                    long actual =
                            track.distanceAt(Duration.ofMillis(Long.parseLong(cells[1]))).meters();
                    expect(failures, line, Long.parseLong(cells[2]), actual);
                }
                case "arrivalTime" -> {
                    checks++;
                    Optional<Duration> actual =
                            track.arrivalTime(Distance.ofMeters(Long.parseLong(cells[1])));
                    String shown = actual.map(d -> Long.toString(d.toMillis())).orElse("NONE");
                    expect(failures, line, cells[2], shown);
                }
                default -> failures.add("모르는 명령: " + line);
            }
        }
        assertThat(failures).isEmpty();
        assertThat(checks).isGreaterThanOrEqualTo(30);
    }

    private static void expect(List<String> failures, String line, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures.add(line + " → 실제 " + actual);
        }
    }

    private static OpponentTrack parseTrack(String cell) {
        List<TrackSample> samples = new ArrayList<>();
        for (String pair : cell.split(";")) {
            String[] parts = pair.split(":");
            samples.add(
                    new TrackSample(
                            Duration.ofMillis(Long.parseLong(parts[0]) * MILLIS_PER_SECOND),
                            Distance.ofMeters(Long.parseLong(parts[1]))));
        }
        return OpponentTrack.of(samples);
    }

    private static OpponentTrack parseUniform(String cell) {
        String[] parts = cell.split(":");
        return OpponentTrack.uniform(
                Distance.ofMeters(Long.parseLong(parts[0])),
                Duration.ofMillis(Long.parseLong(parts[1])));
    }

    private static List<String> lines() throws IOException {
        try (InputStream in = OpponentTrackContractTest.class.getResourceAsStream(CSV)) {
            assertThat(in).as("계약 표 " + CSV).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}

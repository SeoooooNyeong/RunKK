package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import java.time.Duration;

/**
 * 유효 러닝 판정 기준(설계 6.1.3, FR-047). STANDARD는 결정 Q-22의 고정값이다.
 *
 * <p>v0.10 변경(FR-047): 이웃 포인트 사이 시속이 maxSpeedKmh를 넘는 구간(순간이동·차량 이동)은 기록 전체를 무효로 만들지 않고 그 구간의 거리와
 * 시간만 뺀다(isOverspeed). 세 조건(거리·시간·평균 페이스)은 뺀 뒤 남은 값에 적용한다. 앱의 달리는 중 거리(RunSession)도 같은 기준값으로 같은 구간을
 * 빼고 센다(android/contract/policy/ValidityRule.txt로 대조).
 *
 * @param minDistance 최소 거리(1km)
 * @param minElapsed 최소 시간(3분)
 * @param fastestPace 가장 빠른 허용 평균 페이스(3:00)
 * @param slowestPace 가장 느린 허용 평균 페이스(15:00)
 * @param maxSpeedKmh 이웃 포인트 사이 시속 상한(25km/h, 넘는 구간은 거리·시간에서 뺀다)
 */
public record ValidityRule(
        Distance minDistance,
        Duration minElapsed,
        Pace fastestPace,
        Pace slowestPace,
        double maxSpeedKmh) {

    public static final ValidityRule STANDARD =
            new ValidityRule(
                    Distance.ofKm(1), Duration.ofMinutes(3), new Pace(180), new Pace(900), 25.0);

    /**
     * 선언 거리가 경로 길이의 이 배수를 넘으면 경로가 뒷받침하지 않는 기록으로 본다(코드 리뷰 2차 L18). GPS 불안정 구간(터널 등)에서 경로가 짧게 잡히는 경우를
     * 넉넉히 허용한다.
     */
    public static final double MAX_DECLARED_TO_PATH_RATIO = 2.0;

    /** 선언 시간이 시작~종료 시간보다 이만큼 넘게 길면 맞지 않는 기록이다(시계 반올림 허용). */
    public static final Duration WALL_CLOCK_TOLERANCE = Duration.ofSeconds(1);

    /** 세 조건 모두. d·t·avg는 초과 구간을 뺀 뒤 남은 거리·시간·평균 페이스다. */
    public boolean isSatisfiedBy(Distance d, Duration t, Pace avg) {
        return d.isAtLeast(minDistance)
                && t.compareTo(minElapsed) >= 0
                && avg.isWithin(fastestPace, slowestPace);
    }

    /** 이웃 포인트 사이 시속이 상한을 넘는가(넘는 구간은 거리·시간에서 뺀다). 상한과 같으면 넘지 않는다. */
    public boolean isOverspeed(double speedKmh) {
        return speedKmh > maxSpeedKmh;
    }

    /**
     * 다섯째 조건(설계 공백, 갱신 요청 CR2-U6): 선언 거리·시간이 경로와 시작~종료 시각으로 뒷받침되는가. 거리 ≤ 경로 길이 × 2, 선언 시간 ≤ 종료 − 시작
     * + 1초. 포인트 2개에 42km를 선언한 기록 같은 위조를 무효로 만든다. v0.10부터 RunRecord는 초과 구간을 뺀 거리와 초과 구간을 뺀 경로 길이를
     * 넘긴다: 순간이동 한 쌍이 경로 길이를 부풀려 위조한 선언 거리를 뒷받침하지 못하게 한다.
     */
    public boolean isBackedBy(Distance declared, Distance path, Duration elapsed, Duration wall) {
        return declared.meters() <= path.meters() * MAX_DECLARED_TO_PATH_RATIO
                && elapsed.compareTo(wall.plus(WALL_CLOCK_TOLERANCE)) <= 0;
    }
}

package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.policy.BodyShapeRule;
import com.a207.runkk.policy.SkinToneRule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 14장 순서 1 도메인 서비스성 클래스: AppearanceCalculator(6.2 A-16, FR-163, FR-164). */
@DisplayName("AppearanceCalculator 체형·피부색")
class AppearanceCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final BodyShapeRule SHAPE = new BodyShapeRule(List.of(0, 1, 3, 5), 4);
    private static final SkinToneRule TONE =
            new SkinToneRule(
                    List.of(
                            LocalTime.of(4, 0),
                            LocalTime.of(7, 0),
                            LocalTime.of(11, 0),
                            LocalTime.of(17, 0),
                            LocalTime.of(21, 0)));

    private final AppearanceCalculator calculator = new AppearanceCalculator();

    @ParameterizedTest
    @CsvSource({"STAGE1", "STAGE2", "STAGE3", "STAGE4"})
    @DisplayName("쉰 날이 4일 이하면 이전 체형과 상관없이 기본 몸(4단계)")
    void bodyShape_recentRunIsBase(BodyShape previous) {
        AppearanceInput in = new AppearanceInput(TODAY.minusDays(1).atTime(7, 0), previous, TODAY);
        assertThat(calculator.bodyShape(in, SHAPE)).isEqualTo(BodyShape.STAGE4);
    }

    @Test
    @DisplayName("쉰 날 4일은 기본 몸, 5일이면 이전 체형에서 한 단계 통통해진다")
    void bodyShape_restDays() {
        LocalDateTime fourDaysAgo = TODAY.minusDays(4).atTime(23, 0);
        LocalDateTime fiveDaysAgo = TODAY.minusDays(5).atTime(23, 0);
        assertThat(
                        calculator.bodyShape(
                                new AppearanceInput(fourDaysAgo, BodyShape.STAGE2, TODAY), SHAPE))
                .isEqualTo(BodyShape.STAGE4);
        assertThat(
                        calculator.bodyShape(
                                new AppearanceInput(fiveDaysAgo, BodyShape.STAGE4, TODAY), SHAPE))
                .isEqualTo(BodyShape.STAGE3);
    }

    @Test
    @DisplayName("유효 러닝이 한 번도 없으면 기본 몸(4단계)이다: 셀카·기본 아바타 몸이 가입 직후부터 같다(2026-11-12 PM 요구)")
    void bodyShape_noRunEver() {
        assertThat(calculator.bodyShape(new AppearanceInput(null, BodyShape.STAGE4, TODAY), SHAPE))
                .isEqualTo(BodyShape.STAGE4);
        assertThat(calculator.bodyShape(new AppearanceInput(null, BodyShape.STAGE1, TODAY), SHAPE))
                .isEqualTo(BodyShape.STAGE4);
    }

    @Test
    @DisplayName("피부색: 시간대별 개수가 가장 큰 톤, 밤은 21시~다음 날 4시")
    void skinTone_mostFrequentBand() {
        List<LocalDateTime> starts =
                List.of(
                        TODAY.minusDays(1).atTime(22, 0),
                        TODAY.minusDays(2).atTime(2, 0),
                        TODAY.minusDays(3).atTime(8, 0));
        assertThat(calculator.skinTone(starts, TONE)).isEqualTo(SkinTone.NIGHT);
    }

    @Test
    @DisplayName("피부색 동률이면 가장 최근 러닝의 시간대")
    void skinTone_tieBreaksByLatest() {
        List<LocalDateTime> starts =
                List.of(
                        TODAY.minusDays(3).atTime(5, 0),
                        TODAY.minusDays(1).atTime(12, 0),
                        TODAY.minusDays(2).atTime(6, 0),
                        TODAY.minusDays(4).atTime(13, 0));
        assertThat(calculator.skinTone(starts, TONE)).isEqualTo(SkinTone.DAYTIME);
    }

    // W8 내 러너(S15P31A207-299, contract-W8.md 2.1)

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    @Test
    @DisplayName("대표 시각: 지금 톤 시간대에 속한 시작 시각 중 가장 많은 시")
    void typicalStartHour_mostFrequentHourInTone() {
        List<LocalDateTime> starts =
                List.of(at(9, 7, 10), at(8, 8, 30), at(7, 7, 50), at(6, 18, 0), at(5, 18, 5));

        assertThat(calculator.typicalStartHour(starts, SkinTone.MORNING, TONE)).hasValue(7);
        assertThat(calculator.typicalStartHour(starts, SkinTone.EVENING, TONE)).hasValue(18);
    }

    @Test
    @DisplayName("대표 시각: 같은 횟수면 가장 최근 러닝의 시, 그 톤의 러닝이 없거나 러닝이 없으면 비어 있다")
    void typicalStartHour_tieAndEmpty() {
        List<LocalDateTime> starts = List.of(at(7, 8, 0), at(9, 9, 15), at(8, 8, 40), at(6, 9, 0));

        assertThat(calculator.typicalStartHour(starts, SkinTone.MORNING, TONE)).hasValue(9);
        assertThat(calculator.typicalStartHour(starts, SkinTone.NIGHT, TONE)).isEmpty();
        assertThat(calculator.typicalStartHour(List.of(), SkinTone.DAYTIME, TONE)).isEmpty();
    }

    @Test
    @DisplayName("밤 톤은 자정을 넘는 시각(21~04시)도 같은 시간대로 센다")
    void typicalStartHour_nightWrapsMidnight() {
        List<LocalDateTime> starts = List.of(at(9, 2, 0), at(8, 2, 30), at(7, 22, 0));

        assertThat(calculator.typicalStartHour(starts, SkinTone.NIGHT, TONE)).hasValue(2);
    }

    @ParameterizedTest
    @CsvSource({"STAGE4, 0", "STAGE3, 1", "STAGE2, 1", "STAGE1, 2"})
    @DisplayName("화면·렌더러 체형 3단계: STAGE4 기본 몸, STAGE2·3 조금 통통, STAGE1 가장 통통")
    void bodyVariant(BodyShape shape, int variant) {
        assertThat(shape.variant()).isEqualTo(variant);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "299, 0", "300, 1", "15400, 51", "29999, 99", "30000, 100", "90000, 100"})
    @DisplayName("러닝량 0~100: 최근 7일 30km면 100, 비례·버림")
    void runAmount(long meters, int amount) {
        assertThat(AppearanceCalculator.runAmount(com.a207.runkk.common.Distance.ofMeters(meters)))
                .isEqualTo(amount);
    }
}

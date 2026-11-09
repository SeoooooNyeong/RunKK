package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.BodyShapeRule;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.SkinToneRule;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W8 내 러너(S15P31A207-299, contract-W8.md 2.1·6절): 최근 7일 횟수·거리, 3단계 체형, 러닝량, 피부톤 대표 시각. */
@DisplayName("내 러너 조회")
class RunnerServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 20, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final long M = 5;

    private final AvatarFakes.Avatars avatars = new AvatarFakes.Avatars();
    private final RunQueryService runs = mock(RunQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final RunnerService service =
            new RunnerService(
                    avatars,
                    runs,
                    policy,
                    new AppearanceCalculator(),
                    AvatarFakes.transactions(),
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @BeforeEach
    void rules() {
        when(policy.bodyShapeRule()).thenReturn(new BodyShapeRule(List.of(0, 1, 3, 5), 4));
        when(policy.skinToneRule())
                .thenReturn(
                        new SkinToneRule(
                                List.of(
                                        LocalTime.of(4, 0),
                                        LocalTime.of(7, 0),
                                        LocalTime.of(11, 0),
                                        LocalTime.of(17, 0),
                                        LocalTime.of(21, 0))));
        when(runs.dailyStats(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(Map.of());
        when(runs.recentStartTimes(anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
    }

    private static DayStats day(LocalDate date, long meters, int count) {
        return new DayStats(date, Distance.ofMeters(meters), Duration.ofMinutes(30), count);
    }

    @Test
    @DisplayName("러닝이 없으면 0회·0m·러닝량 0, 기본 체형(변화 0)·낮 톤, 대표 시각 없음")
    void noRuns() {
        RunnerView view = service.runner(M);

        assertThat(view.bodyShape()).isEqualTo(BodyShape.STAGE1);
        assertThat(view.bodyVariant()).isZero();
        assertThat(view.skinTone()).isEqualTo(SkinTone.DAYTIME);
        assertThat(view.recentRunCount()).isZero();
        assertThat(view.recentDistance()).isEqualTo(Distance.ofMeters(0));
        assertThat(view.runAmount()).isZero();
        assertThat(view.restDaysBeforeDecline()).isEqualTo(4);
        assertThat(view.typicalStartHour()).isNull();
    }

    @Test
    @DisplayName("오늘을 포함한 최근 7일(오늘−6일 ~ 오늘) 유효 기록을 세고, 저장된 체형·톤으로 단계와 대표 시각을 낸다")
    void countsSevenDaysIncludingToday() {
        avatars.save(avatarWith(BodyShape.STAGE3, SkinTone.MORNING));
        DateRange week = new DateRange(TODAY.minusDays(6), TODAY);
        when(runs.dailyStats(eq(M), eq(week)))
                .thenReturn(
                        Map.of(
                                TODAY,
                                day(TODAY, 5_000, 1),
                                TODAY.minusDays(6),
                                day(TODAY.minusDays(6), 10_400, 2)));
        when(runs.recentStartTimes(M, 10))
                .thenReturn(
                        List.of(
                                TODAY.atTime(7, 5),
                                TODAY.minusDays(1).atTime(7, 40),
                                TODAY.minusDays(2).atTime(19, 0)));

        RunnerView view = service.runner(M);

        verify(runs).dailyStats(M, week);
        assertThat(view.recentRunCount()).isEqualTo(3);
        assertThat(view.recentDistance()).isEqualTo(Distance.ofMeters(15_400));
        assertThat(view.runAmount()).isEqualTo(51);
        assertThat(view.bodyShape()).isEqualTo(BodyShape.STAGE3);
        assertThat(view.bodyVariant()).isEqualTo(1);
        assertThat(view.skinTone()).isEqualTo(SkinTone.MORNING);
        assertThat(view.typicalStartHour()).isEqualTo(7);
    }

    private static Avatar avatarWith(BodyShape shape, SkinTone tone) {
        Avatar avatar = Avatar.create(M);
        avatar.updateAppearance(shape, tone);
        return avatar;
    }
}

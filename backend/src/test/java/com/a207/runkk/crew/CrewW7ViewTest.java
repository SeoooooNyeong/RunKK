package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberProfile;
import com.a207.runkk.member.ProfileVisibility;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 피그마 맞춤 W7(contract-W7.md): 지난 목표 한 주의 표기·진행률, 크루원 보조 정보의 공개 규칙. */
@DisplayName("crew W7 지난 목표 표기·크루원 공개 규칙")
class CrewW7ViewTest {

    @ParameterizedTest(name = "{0} 주 → {1}월 {2}주차")
    @CsvSource({
        "2026-10-12, 10, 3",
        "2026-09-28, 10, 1", // 목요일 10-01이 10월이라 10월 1주차
        "2026-09-21, 9, 4",
        "2026-10-26, 10, 5",
        "2025-12-29, 1, 1", // 해를 넘는 주: 목요일 2026-01-01
    })
    @DisplayName("N월 N주차는 그 주 목요일이 속한 달과 그 달의 몇 번째 목요일인가")
    void weekLabel_followsThursday(LocalDate monday, int month, int weekOfMonth) {
        GoalHistoryView.Line line =
                GoalHistoryView.Line.of(
                        new GoalHistoryRow(
                                monday, Distance.ofMeters(1_000), Distance.ZERO, false, 0));
        assertThat(line.month()).isEqualTo(month);
        assertThat(line.weekOfMonth()).isEqualTo(weekOfMonth);
    }

    @Test
    @DisplayName("진행률은 내림한 백분율이고 100을 넘을 수 있다(C2)")
    void progress_isFlooredAndUncapped() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        assertThat(line(monday, 50_000, 61_500).progressPercent()).isEqualTo(123);
        assertThat(line(monday, 80_000, 72_000).progressPercent()).isEqualTo(90);
        assertThat(line(monday, 3_000, 2_999).progressPercent()).isEqualTo(99);
        assertThat(line(monday, 10_000, 0).progressPercent()).isZero();
    }

    private static GoalHistoryView.Line line(LocalDate monday, long goal, long total) {
        return GoalHistoryView.Line.of(
                new GoalHistoryRow(
                        monday, Distance.ofMeters(goal), Distance.ofMeters(total), false, 3));
    }

    private static final MemberProfile PROFILE =
            new MemberProfile(
                    23,
                    "소개",
                    "20대",
                    Gender.MALE,
                    ProfileVisibility.PUBLIC,
                    ProfileVisibility.PRIVATE);

    @Test
    @DisplayName("다른 크루원에게는 공개한 나이대·성별만, 공개 설정 두 값은 싣지 않는다")
    void others_seeOnlyPublicItems() {
        CrewMemberProfile shown = CrewMemberProfile.shownTo(PROFILE, false);
        assertThat(shown.level()).isEqualTo(23);
        assertThat(shown.ageBand()).isEqualTo("20대");
        assertThat(shown.gender()).isNull();
        assertThat(shown.ageBandVisibility()).isNull();
        assertThat(shown.genderVisibility()).isNull();
    }

    @Test
    @DisplayName("본인은 비공개 항목과 공개 설정까지 본다")
    void self_seesEverything() {
        CrewMemberProfile shown = CrewMemberProfile.shownTo(PROFILE, true);
        assertThat(shown.ageBand()).isEqualTo("20대");
        assertThat(shown.gender()).isEqualTo(Gender.MALE);
        assertThat(shown.ageBandVisibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(shown.genderVisibility()).isEqualTo(ProfileVisibility.PRIVATE);
    }

    @Test
    @DisplayName("회원 정보를 못 읽으면 모두 비어 있다")
    void missingProfile_isEmpty() {
        CrewMemberProfile shown = CrewMemberProfile.shownTo(null, true);
        assertThat(shown.level()).isNull();
        assertThat(shown.ageBand()).isNull();
        assertThat(shown.gender()).isNull();
        assertThat(shown.ageBandVisibility()).isNull();
    }
}

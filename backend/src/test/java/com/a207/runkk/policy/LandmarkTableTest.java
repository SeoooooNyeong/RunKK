package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 6.2 A-18 LandmarkTable.personalProgressOf / crewProgressOf, LandmarkStage 상수별 label(srs.md
 * 3.3.1).
 */
@org.junit.jupiter.api.DisplayName("랜드마크 표기")
class LandmarkTableTest {

    private final LandmarkTable table =
            (LandmarkTable) SettingKey.LANDMARKS.parse(SettingKey.LANDMARKS.defaultValue());

    @Test
    void beforeFirstLandmark_showsProgressToFirst() {
        LandmarkProgress progress = table.personalProgressOf(Distance.ofKm(30));
        assertThat(progress.stage()).isEqualTo(LandmarkStage.DOMESTIC);
        assertThat(progress.name()).isEqualTo("인천");
        assertThat(progress.label()).isEqualTo("서울–인천 왕복까지 50%");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "60|인천|서울에서 인천까지 달렸어요! (왕복 1회)",
                "149|인천|서울에서 인천까지 달렸어요! (왕복 2회)",
                "150|춘천|서울에서 춘천까지 달렸어요! (왕복 1회)",
                "151|춘천|서울에서 춘천까지 달렸어요! (왕복 1회)",
                "299|대전|서울에서 대전까지 달렸어요! (왕복 1회)",
                "279|춘천|서울에서 춘천까지 달렸어요! (왕복 1회)",
                "920|제주도|서울에서 제주도까지 달렸어요! (왕복 1회)",
                "960|베이징|서울에서 베이징까지 달렸어요!",
                "8900|런던|서울에서 런던까지 달렸어요!",
                "40075|지구 둘레 1바퀴|지구 1바퀴째 도는 중!",
                "90000|지구 둘레 1바퀴|지구 2바퀴째 도는 중!",
                "500000|지구 둘레 1바퀴|지구 12바퀴째 도는 중!"
            })
    void personal_usesStagesOneToThreeOnly(long km, String name, String label) {
        LandmarkProgress progress = table.personalProgressOf(Distance.ofKm(km));
        assertThat(progress.name()).isEqualTo(name);
        assertThat(progress.label()).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "960|베이징|서울에서 베이징까지 달렸어요!",
                "80150|지구 2바퀴|지구 2바퀴째 도는 중!",
                "200000|20만 km|지구 4바퀴째 도는 중!",
                "380000|달나라 진입|달나라를 향해 달리는 중! (달까지 98%)",
                "384400|달까지|달에서 돌아오는 중 (0%)",
                "576600|50만 km|달에서 돌아오는 중 (50%)",
                "768800|달 왕복|달까지 다녀왔어요! (달 왕복 1회)",
                "1600000|100만 km|달까지 다녀왔어요! (달 왕복 2회)"
            })
    void crew_includesCrewTotalStage(long km, String name, String label) {
        LandmarkProgress progress = table.crewProgressOf(Distance.ofKm(km));
        assertThat(progress.name()).isEqualTo(name);
        assertThat(progress.label()).isEqualTo(label);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("D-성-129 춘천 149.999km는 아직 인천, 150km 정각부터 춘천(왕복 1회)")
    void chuncheonBoundary() {
        assertThat(table.personalProgressOf(Distance.ofMeters(149_999)).name()).isEqualTo("인천");
        LandmarkProgress at = table.personalProgressOf(Distance.ofMeters(150_000));
        assertThat(at.name()).isEqualTo("춘천");
        assertThat(at.label()).isEqualTo("서울에서 춘천까지 달렸어요! (왕복 1회)");
    }

    @Test
    void crewAndPersonalDiffer_onlyInStageRange() {
        Distance total = Distance.ofKm(100_000);
        assertThat(table.personalProgressOf(total).stage())
                .isEqualTo(LandmarkStage.INTERCONTINENTAL);
        assertThat(table.crewProgressOf(total).stage()).isEqualTo(LandmarkStage.CREW_TOTAL);
    }
}

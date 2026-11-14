package com.a207.runkk.web.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.analysis.RankEntry;
import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingView;
import com.a207.runkk.avatar.RunnerLook;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.Tier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 랭킹 줄 look 전송 형식(S15P31A207-300, 앱과 합의한 고정 계약). */
@org.junit.jupiter.api.DisplayName("랭킹 줄 look 전송 형식")
class RankingLookDtoTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 13, 0, 0);

    private static RankEntry entry(int rank, long id) {
        return new RankEntry(
                rank, id, DisplayName.of("러너" + id), Tier.GOLD, Distance.ofMeters(5_000));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "개인 랭킹 줄·mine에 look{faceKey, outfit{부위 이름: 그림 키}, bodyVariant, runAmount}, 모습을 모르는 줄은"
                    + " null")
    void personalEntriesCarryLook() {
        RankEntry alice = entry(1, 1);
        RankEntry withdrawn = entry(2, 2);
        RankingView view =
                new RankingView(
                        RankingKind.PERSONAL,
                        AggregationPeriod.WEEKLY,
                        AT,
                        List.of(alice, withdrawn),
                        alice,
                        null,
                        null,
                        Map.of(),
                        Map.of(1L, Tier.GOLD));
        RunnerLook look =
                new RunnerLook(
                        "head:1:abc",
                        Map.of(ItemSlot.SHOES, "shoes_basic_runner", ItemSlot.TOP, "top_crew_tee"),
                        1,
                        37);

        JsonNode body = JSON.valueToTree(RecordDtos.RankingResponse.of(view, Map.of(1L, look)));

        JsonNode first = body.get("entries").get(0).get("look");
        assertThat(first.fieldNames())
                .toIterable()
                .containsExactly("faceKey", "outfit", "bodyVariant", "runAmount");
        assertThat(first.get("faceKey").asText()).isEqualTo("head:1:abc");
        assertThat(first.get("outfit").fieldNames()).toIterable().containsExactly("TOP", "SHOES");
        assertThat(first.get("outfit").get("TOP").asText()).isEqualTo("top_crew_tee");
        assertThat(first.get("bodyVariant").asInt()).isEqualTo(1);
        assertThat(first.get("runAmount").asInt()).isEqualTo(37);
        assertThat(body.get("mine").get("look")).isEqualTo(first);
        assertThat(body.get("entries").get(1).get("look").isNull()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루 랭킹 줄은 look이 null이다")
    void crewEntriesHaveNoLook() {
        RankEntry crew = new RankEntry(1, 1, DisplayName.of("크루"), null, Distance.ofMeters(5_000));
        RankingView view =
                new RankingView(
                        RankingKind.CREW,
                        AggregationPeriod.WEEKLY,
                        AT,
                        List.of(crew),
                        crew,
                        null,
                        null,
                        Map.of());

        JsonNode body = JSON.valueToTree(RecordDtos.RankingResponse.of(view));

        assertThat(body.get("entries").get(0).has("look")).isTrue();
        assertThat(body.get("entries").get(0).get("look").isNull()).isTrue();
        assertThat(body.get("mine").get("look").isNull()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("look을 붙일 회원은 개인 랭킹 줄 중 탈퇴하지 않은(공개 티어가 있는) 회원이다")
    void lookTargetsAreActivePersonalMembers() {
        RankingView personal =
                new RankingView(
                        RankingKind.PERSONAL,
                        AggregationPeriod.WEEKLY,
                        AT,
                        List.of(entry(1, 1), entry(2, 2)),
                        entry(3, 3),
                        null,
                        null,
                        Map.of(),
                        Map.of(1L, Tier.GOLD, 3L, Tier.UNRANKED));

        assertThat(personal.activeMemberIds()).containsExactlyInAnyOrder(1L, 3L);
    }
}

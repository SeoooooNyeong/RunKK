package com.a207.runkk.web.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.analysis.RankEntry;
import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.analysis.RankingView;
import com.a207.runkk.avatar.RunnerLook;
import com.a207.runkk.avatar.RunnerLookQuery;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 개인 랭킹 응답에 줄 모습(look)을 붙일지 정하는 분기(RankingController.looksOf, S15P31A207-300). 스냅숏이 없거나 모습을 붙일
 * 회원(탈퇴하지 않은 개인 줄 회원)이 없으면 모습을 읽지 않고, 있으면 그 스냅숏 식별자로 한 번 읽는다.
 */
@org.junit.jupiter.api.DisplayName("개인 랭킹 줄 모습을 읽는 조건")
class RankingControllerLookTest {

    private static final long ME = 1;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 14, 0, 0);

    private final RankingService rankings = mock(RankingService.class);
    private final RunnerLookQuery looks = mock(RunnerLookQuery.class);
    private final RankingController controller = new RankingController(rankings, looks);

    private static RankEntry entry(int rank, long id) {
        return new RankEntry(
                rank, id, DisplayName.of("러너" + id), Tier.GOLD, Distance.ofMeters(5_000));
    }

    private static RankingView view(LocalDateTime at, Map<Long, Tier> publicTiers) {
        return new RankingView(
                RankingKind.PERSONAL,
                AggregationPeriod.WEEKLY,
                at,
                at == null ? List.of() : List.of(entry(1, 1), entry(2, 2)),
                null,
                null,
                null,
                Map.of(),
                publicTiers);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("스냅숏이 아직 없으면(집계 시각 null) 모습을 읽지 않는다")
    void noSnapshotReadsNoLook() {
        when(rankings.personalRanking(ME, AggregationPeriod.WEEKLY))
                .thenReturn(view(null, Map.of()));

        RecordDtos.RankingResponse response = controller.personal(ME, AggregationPeriod.WEEKLY);

        assertThat(response.entries()).isEmpty();
        verifyNoInteractions(looks);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("줄이 모두 탈퇴 회원이면(공개 티어 없음) 모습을 읽지 않고 모든 줄 look이 null이다")
    void allWithdrawnReadsNoLook() {
        when(rankings.personalRanking(ME, AggregationPeriod.WEEKLY)).thenReturn(view(AT, Map.of()));

        RecordDtos.RankingResponse response = controller.personal(ME, AggregationPeriod.WEEKLY);

        assertThat(response.entries()).hasSize(2).allSatisfy(e -> assertThat(e.look()).isNull());
        verifyNoInteractions(looks);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("탈퇴하지 않은 줄 회원만 스냅숏 식별자 {종류}:{기간}:{집계 시각 밀리초}로 읽는다")
    void activeMembersReadBySnapshot() {
        when(rankings.personalRanking(ME, AggregationPeriod.WEEKLY))
                .thenReturn(view(AT, Map.of(1L, Tier.GOLD)));
        String snapshot = "PERSONAL:WEEKLY:" + AT.toInstant(ZoneOffset.UTC).toEpochMilli();
        when(looks.snapshotLooksOf(anyString(), eq(Set.of(1L))))
                .thenReturn(Map.of(1L, new RunnerLook("default:FOX", Map.of(), 1, 30)));

        RecordDtos.RankingResponse response = controller.personal(ME, AggregationPeriod.WEEKLY);

        verify(looks).snapshotLooksOf(snapshot, Set.of(1L));
        assertThat(response.entries().get(0).look().faceKey()).isEqualTo("default:FOX");
        assertThat(response.entries().get(1).look()).as("탈퇴 회원 줄").isNull();
    }
}

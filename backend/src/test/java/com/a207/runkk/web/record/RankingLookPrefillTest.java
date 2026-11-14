package com.a207.runkk.web.record;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingSnapshotRebuilt;
import com.a207.runkk.analysis.RankingView;
import com.a207.runkk.avatar.RunnerLookQuery;
import com.a207.runkk.common.AggregationPeriod;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 0시 재집계 뒤 개인 랭킹 상위 줄 모습 미리 채우기(RankingLookPrefill, [임시:DF-성-20], PM 2026-11-14). 조회와 같은 스냅숏 식별자로
 * 저장하고, 크루·빈 줄은 건너뛰며, 실패는 삼켜 재집계를 실패시키지 않는다(첫 조회 지연 채움이 안전망).
 */
@DisplayName("랭킹 재집계 뒤 줄 모습 미리 채우기")
class RankingLookPrefillTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 14, 0, 0, 0, 7_000_000);

    private final RunnerLookQuery looks = mock(RunnerLookQuery.class);
    private final RankingLookPrefill prefill =
            new RankingLookPrefill(looks, mock(PlatformTransactionManager.class));

    @Test
    @DisplayName("개인 스냅숏: 상위 줄 회원 모습을 조회 쪽(RankingController)과 같은 스냅숏 식별자로 저장한다")
    void personalSnapshotIsPrefilledWithQueryKey() {
        prefill.onRebuilt(
                new RankingSnapshotRebuilt(
                        RankingKind.PERSONAL, AggregationPeriod.WEEKLY, AT, List.of(3L, 1L)));

        RankingView sameSnapshot =
                new RankingView(
                        RankingKind.PERSONAL,
                        AggregationPeriod.WEEKLY,
                        AT,
                        List.of(),
                        null,
                        null,
                        null,
                        Map.of());
        String queryKey = RankingController.snapshotOf(sameSnapshot);
        verify(looks).snapshotLooksOf(queryKey, List.of(3L, 1L));
        org.assertj.core.api.Assertions.assertThat(queryKey)
                .as("집계 시각을 UTC로 본 밀리초(RankingCache 판 번호와 같은 식, U26-2)")
                .isEqualTo("PERSONAL:WEEKLY:" + AT.toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    @Test
    @DisplayName("크루 스냅숏과 줄 없는 스냅숏은 모습을 읽지 않는다")
    void crewOrEmptySnapshotIsSkipped() {
        prefill.onRebuilt(
                new RankingSnapshotRebuilt(
                        RankingKind.CREW, AggregationPeriod.WEEKLY, AT, List.of(7L)));
        prefill.onRebuilt(
                new RankingSnapshotRebuilt(
                        RankingKind.PERSONAL, AggregationPeriod.MONTHLY, AT, List.of()));

        verifyNoInteractions(looks);
    }

    @Test
    @DisplayName("미리 채우기가 실패해도 예외를 밖으로 던지지 않는다(재집계는 성공, 첫 조회가 채운다)")
    void failureIsSwallowed() {
        when(looks.snapshotLooksOf(anyString(), anyCollection()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(
                        () ->
                                prefill.onRebuilt(
                                        new RankingSnapshotRebuilt(
                                                RankingKind.PERSONAL,
                                                AggregationPeriod.WEEKLY,
                                                AT,
                                                List.of(1L))))
                .doesNotThrowAnyException();
    }
}

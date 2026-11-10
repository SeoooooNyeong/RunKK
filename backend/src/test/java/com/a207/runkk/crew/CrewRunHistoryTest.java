package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.Distance;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 최근 크루런 거리 합(코드 리뷰 M3): 연결 기록 요약을 글·참가자마다 따로 읽지 않고 한 번에 읽는다. */
@DisplayName("크루 최근 크루런 요약(M3)")
class CrewRunHistoryTest {

    private final CrewPostRepository posts = mock(CrewPostRepository.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final CrewRunHistory history = new CrewRunHistory(posts, runs);

    /** 작성자(LEADER)가 달려 recordId 기록을 연결했거나(null이면 연결 없음) 끝낼 때 finalMeters를 남긴 크루런. */
    private static CrewRunPost run(long id, String recordId, Long finalMeters) {
        CrewRunPost post = CrewFixtures.crewRun(1L, NOW.plusDays(id));
        ReflectionTestUtils.setField(post, "id", id);
        CrewRunParticipation p = post.participations().get(0);
        p.markRunner();
        if (finalMeters != null) {
            p.end(Distance.ofMeters(finalMeters), Duration.ofMinutes(30));
        }
        if (recordId != null) {
            p.link(recordId);
        }
        return post;
    }

    private static RunSummary summary(String recordId, long meters) {
        return new RunSummary(
                recordId,
                LEADER,
                NOW,
                Distance.ofMeters(meters),
                Duration.ofMinutes(30),
                null,
                true,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("기록 요약은 한 번의 일괄 조회로 읽고, 없는 기록은 끝낸 거리 → 0으로 넘어간다")
    void recent_readsSummariesInOneBatch() {
        when(posts.findRecentFinishedCrewRuns(1L, CrewDetailView.RECENT_CREW_RUNS))
                .thenReturn(List.of(run(3, "r3", null), run(2, "gone", 700L), run(1, null, null)));
        when(runs.summariesOf(anyCollection())).thenReturn(Map.of("r3", summary("r3", 5_000)));

        List<CrewDetailView.RecentCrewRun> recent = history.recent(1L);

        assertThat(recent)
                .extracting(CrewDetailView.RecentCrewRun::totalDistance)
                .containsExactly(Distance.ofMeters(5_000), Distance.ofMeters(700), Distance.ZERO);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(runs, times(1)).summariesOf(ids.capture());
        assertThat(Set.copyOf(ids.getValue())).containsExactlyInAnyOrder("r3", "gone");
        verify(runs, never()).findSummary(any());
    }

    @Test
    @DisplayName("연결된 기록이 없으면 기록 요약을 읽지 않는다")
    void recent_withoutLinkedRecords_skipsQuery() {
        when(posts.findRecentFinishedCrewRuns(1L, CrewDetailView.RECENT_CREW_RUNS))
                .thenReturn(List.of(run(1, null, 300L)));

        assertThat(history.recent(1L))
                .extracting(CrewDetailView.RecentCrewRun::totalDistance)
                .containsExactly(Distance.ofMeters(300));
        verify(runs, never()).summariesOf(anyCollection());
        verify(runs, never()).findSummary(any());
    }
}

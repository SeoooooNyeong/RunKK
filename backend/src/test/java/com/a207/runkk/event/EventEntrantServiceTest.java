package com.a207.runkk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.CelebrityWin;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("event.EventEntrantService 응모자 명단")
class EventEntrantServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 16, 23, 59, 59);

    private final InMemoryEventRepository events = new InMemoryEventRepository();
    private final BattleHistoryService battles = mock(BattleHistoryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final EventEntrantService service =
            new EventEntrantService(events, battles, runs, members);

    private long coachId;
    private long runId;

    @BeforeEach
    void events() {
        coachId =
                events.save(Event.create(EventServiceTest.coach("coach-challenge", 1), START))
                        .getId();
        runId =
                events.save(Event.create(EventServiceTest.run("beta-1km", START, END, 2), START))
                        .getId();
        // 3번은 탈퇴(닉네임 없음)
        when(members.activeNicknames(anyCollection()))
                .thenAnswer(
                        a -> {
                            Map<Long, String> names = new HashMap<>();
                            for (Long id : a.<Collection<Long>>getArgument(0)) {
                                if (id != 3L) {
                                    names.put(id, "러너" + id);
                                }
                            }
                            return names;
                        });
        when(battles.celebrityNames(List.of(11L, 12L))).thenReturn(Map.of(11L, "주세빈", 12L, "김선교"));
    }

    @Test
    @DisplayName("COACH_CELEB: 이긴 회원마다 셀럽별 첫 판정 시각, 회원 ID 내림차순, 탈퇴 회원 제외, 셀럽으로 거르기")
    void coachEntrants() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 9, 19, 0);
        when(battles.celebrityWinners(List.of(11L, 12L), START, END))
                .thenReturn(
                        List.of(
                                new CelebrityWin(1, 11, t),
                                new CelebrityWin(2, 11, t.plusHours(1)),
                                new CelebrityWin(2, 12, t.plusHours(2)),
                                new CelebrityWin(3, 12, t)));

        EventEntrants all = service.entrants(coachId, null, PageCursor.all());

        assertThat(all.total()).isEqualTo(2);
        assertThat(all.entrants())
                .extracting(EventEntrants.Entrant::memberId)
                .containsExactly(2L, 1L);
        EventEntrants.Entrant two = all.entrants().get(0);
        assertThat(two.nickname()).isEqualTo("러너2");
        assertThat(two.entryCount()).isEqualTo(2);
        assertThat(two.items())
                .extracting(EventEntry.Item::key, EventEntry.Item::label, EventEntry.Item::doneAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("celebrity-11", "주세빈", t.plusHours(1)),
                        org.assertj.core.groups.Tuple.tuple("celebrity-12", "김선교", t.plusHours(2)));
        assertThat(all.entrants().get(1).entryCount()).isEqualTo(1);
        assertThat(all.entrants().get(1).items().get(1).done()).isFalse();

        EventEntrants only12 = service.entrants(coachId, 12L, PageCursor.all());
        assertThat(only12.celebrityId()).isEqualTo(12L);
        assertThat(only12.entrants())
                .extracting(EventEntrants.Entrant::memberId)
                .containsExactly(2L);

        EventEntrants page = service.entrants(coachId, null, new PageCursor(null, 1));
        assertThat(page.entrants()).hasSize(1);
        assertThat(page.nextCursor()).isEqualTo(2L);
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("RUN_DISTANCE: 회원마다 기준 거리 이상 첫 유효 기록의 시작 시각과 거리")
    void runEntrants() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 10, 7, 30);
        Map<Long, RunSummary> first = new TreeMap<>();
        first.put(4L, summary(4, t, 1_120));
        first.put(3L, summary(3, t, 1_000));
        when(runs.earliestValidRunsAtLeast(1_000, START, END)).thenReturn(first);

        EventEntrants all = service.entrants(runId, null, PageCursor.all());

        assertThat(all.entrants()).extracting(EventEntrants.Entrant::memberId).containsExactly(4L);
        EventEntry.Item item = all.entrants().get(0).items().get(0);
        assertThat(item.key()).isEqualTo("run-1km");
        assertThat(item.doneAt()).isEqualTo(t);
        assertThat(item.distanceMeters()).isEqualTo(1_120L);
        assertThat(all.entrants().get(0).entryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 이벤트는 404, 이벤트 셀럽이 아니거나 기록 거리 이벤트를 셀럽으로 거르면 400")
    void rejectsUnknownEventAndBadFilter() {
        assertThatThrownBy(() -> service.entrants(999, null, PageCursor.all()))
                .isInstanceOf(NotFoundRuleException.class);
        assertThatThrownBy(() -> service.entrants(coachId, 99L, PageCursor.all()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.entrants(runId, 11L, PageCursor.all()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RunSummary summary(long memberId, LocalDateTime startedAt, long meters) {
        return new RunSummary(
                "r-" + memberId,
                memberId,
                startedAt,
                Distance.ofMeters(meters),
                Duration.ofMinutes(12),
                new Pace(600),
                true,
                50.0,
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null);
    }
}

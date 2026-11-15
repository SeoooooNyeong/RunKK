package com.a207.runkk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.CelebrityChallenge;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 이벤트 목록 정렬과 상태, 응모 계산 두 종류, 관리 검사와 감사(S15P31A207-275). */
@DisplayName("event.EventService")
class EventServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 16, 23, 59, 59);
    private static final long MEMBER = 7L;
    private static final String OPERATOR = "KAKAO:op";

    private final InMemoryEventRepository events = new InMemoryEventRepository();
    private final BattleHistoryService battles = mock(BattleHistoryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final InMemoryAuditRepository audits = new InMemoryAuditRepository();
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final AuditService auditService = new AuditService(audits, clock);
    private final EventService service =
            new EventService(events, battles, runs, auditService, clock);

    static EventInput input(
            String slug,
            EventKind kind,
            String config,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int sortOrder,
            boolean published) {
        return new EventInput(
                slug,
                "제목 " + slug,
                "부제",
                kind,
                config,
                startsAt,
                endsAt,
                "/events/" + slug + "/banner.png",
                "/events/" + slug + "/thumb.png",
                "/events/" + slug + "/",
                sortOrder,
                published);
    }

    static EventInput coach(String slug, int sortOrder) {
        return input(
                slug,
                EventKind.COACH_CELEB,
                "{\"celebrityIds\":[11,12],\"pointPerWin\":500}",
                START,
                END,
                sortOrder,
                true);
    }

    static EventInput run(String slug, LocalDateTime startsAt, LocalDateTime endsAt, int order) {
        return input(
                slug,
                EventKind.RUN_DISTANCE,
                "{\"minDistanceMeters\":1000}",
                startsAt,
                endsAt,
                order,
                true);
    }

    private static RunSummary summary(LocalDateTime startedAt, long meters, boolean valid) {
        return new RunSummary(
                "r-" + startedAt + "-" + meters,
                MEMBER,
                startedAt,
                Distance.ofMeters(meters),
                Duration.ofMinutes(12),
                new Pace(600),
                valid,
                valid ? 50.0 : null,
                RunMode.FREE,
                CardKind.FREE_RUN,
                null,
                null);
    }

    @Test
    @DisplayName("목록은 공개만, 진행 중 → 예정 → 종료 순이고 같은 상태 안은 sortOrder 오름차순, 상태는 지금 시각으로 계산한다")
    void list_sortsByStatusThenOrder() {
        when(runs.summariesBetween(anyLong(), any())).thenReturn(List.of());
        service.create(OPERATOR, run("ended", NOW.minusDays(5), NOW.minusDays(3), 0));
        service.create(OPERATOR, run("upcoming", NOW.plusDays(1), NOW.plusDays(5), 0));
        service.create(OPERATOR, run("ongoing-b", NOW.minusDays(1), NOW.plusDays(1), 2));
        service.create(OPERATOR, run("ongoing-a", NOW.minusDays(1), NOW.plusDays(1), 1));
        EventInput hidden = run("hidden", NOW.minusDays(1), NOW.plusDays(1), 0);
        service.create(
                OPERATOR,
                new EventInput(
                        hidden.slug(),
                        hidden.title(),
                        hidden.subtitle(),
                        hidden.kind(),
                        hidden.config(),
                        hidden.startsAt(),
                        hidden.endsAt(),
                        hidden.bannerImageUrl(),
                        hidden.thumbnailImageUrl(),
                        hidden.detailUrl(),
                        0,
                        false));

        List<EventView> list = service.list(MEMBER);

        assertThat(list)
                .extracting(EventView::slug)
                .containsExactly("ongoing-a", "ongoing-b", "upcoming", "ended");
        assertThat(list)
                .extracting(EventView::status)
                .containsExactly(
                        EventStatus.ONGOING,
                        EventStatus.ONGOING,
                        EventStatus.UPCOMING,
                        EventStatus.ENDED);
        assertThat(list.get(0).maxEntryCount()).isEqualTo(1);
        assertThat(list.get(0).detailUrl()).isEqualTo("/events/ongoing-a/");
    }

    @Test
    @DisplayName("시작 시각과 종료 시각 자체는 진행 중이다(양 끝 포함)")
    void status_includesBothEnds() {
        assertThat(EventStatus.of(START, END, START)).isEqualTo(EventStatus.ONGOING);
        assertThat(EventStatus.of(START, END, END)).isEqualTo(EventStatus.ONGOING);
        assertThat(EventStatus.of(START, END, START.minusNanos(1))).isEqualTo(EventStatus.UPCOMING);
        assertThat(EventStatus.of(START, END, END.plusSeconds(1))).isEqualTo(EventStatus.ENDED);
    }

    @Test
    @DisplayName("COACH_CELEB: 기간 안에 이긴 셀럽 수가 응모 수(셀럽마다 최대 1), 첫 승리 시각이 doneAt, 지워진 기록은 대체 이름")
    void coachEntry_countsWonCelebrities() {
        service.create(OPERATOR, coach("coach-challenge", 1));
        LocalDateTime won = LocalDateTime.of(2026, 11, 9, 19, 2, 11);
        when(battles.celebrityChallenges(MEMBER, List.of(11L, 12L), START, END))
                .thenReturn(
                        List.of(
                                new CelebrityChallenge(11, "주세빈 코치님", won),
                                new CelebrityChallenge(12, null, null)));

        EventEntry entry = service.entry(MEMBER, "coach-challenge");

        assertThat(entry.entryCount()).isEqualTo(1);
        assertThat(entry.maxEntryCount()).isEqualTo(2);
        assertThat(entry.items())
                .containsExactly(
                        new EventEntry.Item("celebrity-11", "주세빈 코치님", true, won, null),
                        new EventEntry.Item("celebrity-12", "셀럽 12", false, null, null));
    }

    @Test
    @DisplayName("RUN_DISTANCE: 기간 안 유효하고 1km 이상인 가장 이른 기록 하나로 응모 1, 무효나 짧거나 기간 밖 기록은 세지 않는다")
    void runEntry_usesEarliestValidLongEnoughRun() {
        service.create(OPERATOR, run("beta-1km", START, END, 2));
        LocalDateTime first = LocalDateTime.of(2026, 11, 9, 7, 30);
        when(runs.summariesBetween(
                        eq(MEMBER),
                        eq(new DateRange(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 16)))))
                .thenReturn(
                        List.of(
                                summary(LocalDateTime.of(2026, 11, 10, 8, 0), 3_000, true),
                                summary(first, 1_043, true),
                                summary(LocalDateTime.of(2026, 11, 8, 6, 0), 2_000, false),
                                summary(LocalDateTime.of(2026, 11, 8, 7, 0), 999, true),
                                summary(START.minusSeconds(1), 5_000, true)));

        EventEntry entry = service.entry(MEMBER, "beta-1km");

        assertThat(entry.entryCount()).isEqualTo(1);
        assertThat(entry.maxEntryCount()).isEqualTo(1);
        assertThat(entry.items())
                .containsExactly(new EventEntry.Item("run-1km", "1km", true, first, 1_043L));
    }

    @Test
    @DisplayName("RUN_DISTANCE: 맞는 기록이 없으면 응모 0, doneAt과 거리는 null")
    void runEntry_notDone() {
        service.create(OPERATOR, run("beta-1km", START, END, 2));
        when(runs.summariesBetween(anyLong(), any()))
                .thenReturn(List.of(summary(LocalDateTime.of(2026, 11, 9, 7, 0), 800, true)));

        EventEntry entry = service.entry(MEMBER, "beta-1km");

        assertThat(entry.entryCount()).isZero();
        assertThat(entry.items())
                .containsExactly(new EventEntry.Item("run-1km", "1km", false, null, null));
    }

    @Test
    @DisplayName("없거나 비공개인 slug는 EVENT_NOT_FOUND(404)")
    void entry_unknownOrHidden_isNotFound() {
        EventAdminView created = service.create(OPERATOR, coach("coach-challenge", 1));
        service.changePublished(OPERATOR, created.id(), false);

        assertThatThrownBy(() -> service.entry(MEMBER, "nope"))
                .isInstanceOf(NotFoundRuleException.class);
        assertThatThrownBy(() -> service.entry(MEMBER, "coach-challenge"))
                .isInstanceOf(NotFoundRuleException.class)
                .extracting(e -> ((NotFoundRuleException) e).reason())
                .isEqualTo(ReasonCode.EVENT_NOT_FOUND);
        assertThat(service.list(MEMBER)).isEmpty();
        assertThat(service.adminList())
                .extracting(EventAdminView::published)
                .containsExactly(false);
    }

    @Test
    @DisplayName("관리: 종류에 맞지 않는 설정, 거꾸로 된 기간, 겹치는 slug는 VALUE_INVALID")
    void admin_rejectsInvalidInput() {
        service.create(OPERATOR, coach("coach-challenge", 1));

        assertThatThrownBy(() -> service.create(OPERATOR, coach("coach-challenge", 2)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.VALUE_INVALID);
        for (String config :
                List.of(
                        "{\"minDistanceMeters\":1000}",
                        "{\"celebrityIds\":[],\"pointPerWin\":500}",
                        "{\"celebrityIds\":[11,11],\"pointPerWin\":500}",
                        "not json",
                        "null")) {
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            OPERATOR,
                                            input(
                                                    "bad",
                                                    EventKind.COACH_CELEB,
                                                    config,
                                                    START,
                                                    END,
                                                    1,
                                                    true)))
                    .as(config)
                    .isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> service.create(OPERATOR, run("reverse", END, START, 1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("관리: 생성, 수정, 공개 전환이 운영자 계정으로 SETTING_CHANGE 감사(대상 EVENT:slug, 전후 값)에 남는다")
    void admin_changesAreAudited() {
        EventAdminView created = service.create(OPERATOR, coach("coach-challenge", 1));
        EventAdminView updated =
                service.update(OPERATOR, created.id(), coach("coach-challenge", 5));
        service.changePublished(OPERATOR, created.id(), false);

        assertThat(updated.sortOrder()).isEqualTo(5);
        List<AuditRecordView> records =
                auditService.query(
                        new DateRange(NOW.toLocalDate(), NOW.toLocalDate()),
                        Optional.of(AuditAction.SETTING_CHANGE));
        assertThat(records).hasSize(3);
        assertThat(records)
                .allSatisfy(
                        r -> {
                            assertThat(r.actorId()).isEqualTo(OPERATOR);
                            assertThat(r.targetId()).isEqualTo("EVENT:coach-challenge");
                        });
        assertThat(records).filteredOn(r -> r.before() == null).hasSize(1);
        assertThatThrownBy(() -> service.update(OPERATOR, 999, coach("x", 1)))
                .isInstanceOf(NotFoundRuleException.class);
    }

    private void assertConfigRejected(EventKind kind, String config) {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        OPERATOR, input("bad", kind, config, START, END, 1, true)))
                .as(kind + " " + config)
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.VALUE_INVALID));
    }

    @Test
    @DisplayName(
            "관리 설정 경곗값: 셀럽 ID 0, -1, null과 승리포인트 없음, -1은 거부하고 셀럽 ID 1, 승리포인트 0과 1은"
                    + " 받는다(TC-2612~TC-2614)")
    void admin_coachConfigBoundaries() {
        for (String config :
                List.of(
                        "{\"celebrityIds\":[0],\"pointPerWin\":500}",
                        "{\"celebrityIds\":[-1],\"pointPerWin\":500}",
                        "{\"celebrityIds\":[null],\"pointPerWin\":500}",
                        "{\"celebrityIds\":[11]}",
                        "{\"celebrityIds\":[11],\"pointPerWin\":-1}")) {
            assertConfigRejected(EventKind.COACH_CELEB, config);
        }
        List<String> accepted =
                List.of(
                        "{\"celebrityIds\":[1],\"pointPerWin\":500}",
                        "{\"celebrityIds\":[11],\"pointPerWin\":0}",
                        "{\"celebrityIds\":[11],\"pointPerWin\":1}");
        for (int i = 0; i < accepted.size(); i++) {
            EventAdminView created =
                    service.create(
                            OPERATOR,
                            input(
                                    "coach-ok-" + i,
                                    EventKind.COACH_CELEB,
                                    accepted.get(i),
                                    START,
                                    END,
                                    1,
                                    true));
            assertThat(created.kind()).as(accepted.get(i)).isEqualTo(EventKind.COACH_CELEB);
        }
    }

    @Test
    @DisplayName("관리 설정 경곗값: 기준 거리 -1, 0, 없음은 거부하고 1은 받는다(TC-2616)")
    void admin_runConfigBoundaries() {
        for (String config :
                List.of(
                        "{\"minDistanceMeters\":-1}",
                        "{\"minDistanceMeters\":0}",
                        "{}",
                        "{\"celebrityIds\":[11],\"pointPerWin\":500}")) {
            assertConfigRejected(EventKind.RUN_DISTANCE, config);
        }
        EventAdminView created =
                service.create(
                        OPERATOR,
                        input(
                                "run-1m",
                                EventKind.RUN_DISTANCE,
                                "{\"minDistanceMeters\":1}",
                                START,
                                END,
                                1,
                                true));
        assertThat(created.kind()).isEqualTo(EventKind.RUN_DISTANCE);
    }

    @Test
    @DisplayName("RUN_DISTANCE: 1,000m 정각과 1,001m는 응모 1, 999m는 0(응모기준거리 이상, TC-2633)")
    void runEntry_countsExactlyMinimumDistance() {
        service.create(OPERATOR, run("beta-1km", START, END, 2));
        LocalDateTime at = LocalDateTime.of(2026, 11, 9, 7, 30);
        for (long meters : List.of(999L, 1_000L, 1_001L)) {
            when(runs.summariesBetween(anyLong(), any()))
                    .thenReturn(List.of(summary(at, meters, true)));

            EventEntry entry = service.entry(MEMBER, "beta-1km");

            assertThat(entry.entryCount()).as(meters + "m").isEqualTo(meters >= 1_000 ? 1 : 0);
        }
    }

    @Test
    @DisplayName("RUN_DISTANCE: 시작 정각과 종료 정각에 시작한 기록은 세고 종료 + 1초는 세지 않는다(양 끝 포함, TC-2634)")
    void runEntry_includesBothEndsOfPeriod() {
        service.create(OPERATOR, run("beta-1km", START, END, 2));
        for (LocalDateTime startedAt : List.of(START, END, END.plusSeconds(1))) {
            when(runs.summariesBetween(anyLong(), any()))
                    .thenReturn(List.of(summary(startedAt, 5_000, true)));

            EventEntry entry = service.entry(MEMBER, "beta-1km");

            assertThat(entry.entryCount())
                    .as(startedAt.toString())
                    .isEqualTo(startedAt.isAfter(END) ? 0 : 1);
        }
    }
}

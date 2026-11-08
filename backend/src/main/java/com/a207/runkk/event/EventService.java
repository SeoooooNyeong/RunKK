package com.a207.runkk.event;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 목록, 내 참여, 운영자 관리(S15P31A207-275). 응모는 저장하지 않고 읽을 때 계산한다. COACH_CELEB는 기간 안에 이긴 셀럽 수(셀럽마다 최대
 * 1, 첫 승리 판정 시각), RUN_DISTANCE는 기간 안 유효 기록 중 기준 거리 이상인 가장 이른 기록 하나다. 기간은 한국 시간이고 양 끝을 포함한다. 관리 변경은
 * 운영 기준값 변경과 같은 감사 행위(SETTING_CHANGE, 대상 "EVENT:slug")로 남긴다.
 */
@Service
public class EventService {

    static final String AUDIT_TARGET_PREFIX = "EVENT:";
    private static final String CELEBRITY_KEY_PREFIX = "celebrity-";
    static final String RUN_KEY_PREFIX = "run-";
    private static final BigDecimal METERS_PER_KM = BigDecimal.valueOf(1_000);

    private final EventRepository eventRepository;
    private final BattleHistoryService battleHistoryService;
    private final RunQueryService runQueryService;
    private final AuditService auditService;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            BattleHistoryService battleHistoryService,
            RunQueryService runQueryService,
            AuditService auditService,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.battleHistoryService = battleHistoryService;
        this.runQueryService = runQueryService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** 공개 이벤트만. 진행 중, 예정, 종료 순이고 같은 상태 안에서는 sortOrder 오름차순이다. */
    @Transactional(readOnly = true)
    public List<EventView> list(long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return eventRepository.findAll().stream()
                .filter(Event::isPublished)
                .map(e -> view(e, entry(e, memberId), now))
                .sorted(
                        Comparator.comparing(EventView::status)
                                .thenComparingInt(EventView::sortOrder)
                                .thenComparingLong(EventView::id))
                .toList();
    }

    /** 공개 이벤트의 내 참여. 없거나 비공개면 404 EVENT_NOT_FOUND. */
    @Transactional(readOnly = true)
    public EventEntry entry(long memberId, String slug) {
        Event event =
                eventRepository
                        .findBySlug(slug)
                        .filter(Event::isPublished)
                        .orElseThrow(() -> new NotFoundRuleException(ReasonCode.EVENT_NOT_FOUND));
        return entry(event, memberId);
    }

    private EventEntry entry(Event event, long memberId) {
        EventConfig config = event.parsedConfig();
        List<EventEntry.Item> items =
                switch (event.getKind()) {
                    case COACH_CELEB -> coachItems(event, config, memberId);
                    case RUN_DISTANCE -> List.of(runItem(event, config, memberId));
                };
        int done = (int) items.stream().filter(EventEntry.Item::done).count();
        return new EventEntry(event.getSlug(), done, items.size(), items);
    }

    private List<EventEntry.Item> coachItems(Event event, EventConfig config, long memberId) {
        return battleHistoryService
                .celebrityChallenges(
                        memberId, config.celebrityIds(), event.getStartsAt(), event.getEndsAt())
                .stream()
                .map(
                        c ->
                                new EventEntry.Item(
                                        celebrityKey(c.celebrityId()),
                                        c.name() == null ? "셀럽 " + c.celebrityId() : c.name(),
                                        c.firstWonAt() != null,
                                        c.firstWonAt(),
                                        null))
                .toList();
    }

    /** 기간이 걸친 날짜의 기록 요약에서 시작 시각이 기간 안이고 유효하며 기준 거리 이상인 가장 이른 기록을 고른다. */
    private EventEntry.Item runItem(Event event, EventConfig config, long memberId) {
        long min = config.minDistanceMeters();
        String label = distanceLabel(min);
        Optional<RunSummary> first =
                runQueryService
                        .summariesBetween(
                                memberId,
                                new DateRange(
                                        event.getStartsAt().toLocalDate(),
                                        event.getEndsAt().toLocalDate()))
                        .stream()
                        .filter(RunSummary::valid)
                        .filter(r -> r.distance().meters() >= min)
                        .filter(r -> event.statusAt(r.startedAt()) == EventStatus.ONGOING)
                        .min(Comparator.comparing(RunSummary::startedAt));
        return new EventEntry.Item(
                RUN_KEY_PREFIX + label,
                label,
                first.isPresent(),
                first.map(RunSummary::startedAt).orElse(null),
                first.map(r -> r.distance().meters()).orElse(null));
    }

    /** 1000 → "1km", 1500 → "1.5km". */
    static String distanceLabel(long meters) {
        return BigDecimal.valueOf(meters).divide(METERS_PER_KM).stripTrailingZeros().toPlainString()
                + "km";
    }

    /** 셀럽 응모 항목 키(포인트 지급 이력의 대상 키와 같다). */
    static String celebrityKey(long celebrityId) {
        return CELEBRITY_KEY_PREFIX + celebrityId;
    }

    private static EventView view(Event e, EventEntry entry, LocalDateTime now) {
        return new EventView(
                e.getId(),
                e.getSlug(),
                e.getTitle(),
                e.getSubtitle(),
                e.statusAt(now),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getBannerImageUrl(),
                e.getThumbnailImageUrl(),
                e.getDetailUrl(),
                entry.entryCount(),
                entry.maxEntryCount(),
                e.getSortOrder());
    }

    // ---------- 관리(web.admin.event, actorId는 OperatorCheckInterceptor가 확인한 운영자 계정) ----------

    /** 비공개를 포함한 전부, sortOrder와 ID 순. */
    @Transactional(readOnly = true)
    public List<EventAdminView> adminList() {
        LocalDateTime now = LocalDateTime.now(clock);
        return eventRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Event::getSortOrder).thenComparing(Event::getId))
                .map(e -> EventAdminView.of(e, now))
                .toList();
    }

    /** 설정, 기간, slug 중복을 검사하고 저장한 뒤 감사를 남긴다. 위반은 409 VALUE_INVALID. */
    @Transactional
    public EventAdminView create(String actorId, EventInput in) {
        validate(in, null);
        LocalDateTime now = LocalDateTime.now(clock);
        Event saved = eventRepository.save(Event.create(in, now));
        EventAdminView after = EventAdminView.of(saved, now);
        audit(actorId, saved.getSlug(), null, after);
        return after;
    }

    /** 모든 항목을 바꾼다. 없으면 404 EVENT_NOT_FOUND, 위반은 409 VALUE_INVALID. */
    @Transactional
    public EventAdminView update(String actorId, long eventId, EventInput in) {
        Event event = find(eventId);
        validate(in, eventId);
        LocalDateTime now = LocalDateTime.now(clock);
        EventAdminView before = EventAdminView.of(event, now);
        event.update(in, now);
        eventRepository.save(event);
        EventAdminView after = EventAdminView.of(event, now);
        audit(actorId, event.getSlug(), before, after);
        return after;
    }

    /** 공개 여부만 바꾼다. 없으면 404 EVENT_NOT_FOUND. */
    @Transactional
    public EventAdminView changePublished(String actorId, long eventId, boolean published) {
        Event event = find(eventId);
        LocalDateTime now = LocalDateTime.now(clock);
        EventAdminView before = EventAdminView.of(event, now);
        event.changePublished(published, now);
        eventRepository.save(event);
        EventAdminView after = EventAdminView.of(event, now);
        audit(actorId, event.getSlug(), before, after);
        return after;
    }

    private Event find(long eventId) {
        return eventRepository
                .findById(eventId)
                .orElseThrow(() -> new NotFoundRuleException(ReasonCode.EVENT_NOT_FOUND));
    }

    private void validate(EventInput in, Long eventId) {
        EventConfig.parse(in.kind(), in.config());
        boolean slugTaken =
                eventRepository
                        .findBySlug(in.slug())
                        .filter(other -> !other.getId().equals(eventId))
                        .isPresent();
        if (!in.endsAt().isAfter(in.startsAt()) || slugTaken) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
    }

    private void audit(String actorId, String slug, EventAdminView before, EventAdminView after) {
        auditService.record(
                new AuditEntry(
                        actorId,
                        ActorRole.OPERATOR,
                        AuditAction.SETTING_CHANGE,
                        AUDIT_TARGET_PREFIX + slug,
                        before == null ? null : before.toString(),
                        after.toString()));
    }
}

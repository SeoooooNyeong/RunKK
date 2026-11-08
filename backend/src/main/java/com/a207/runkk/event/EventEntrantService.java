package com.a207.runkk.event;

import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.CelebrityWin;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 응모자 명단(관리, 추첨용, S15P31A207-275, 2026-11-08 사용자 결정 "추첨 명단은 관리 API로"). 응모는 저장하지 않으므로 내 참여와 같은
 * 규칙으로 모든 회원을 한 번에 센다. COACH_CELEB은 셀럽 기록을 기간 안에 이긴 회원(셀럽마다 첫 판정 시각), RUN_DISTANCE는 기간 안 시작한 유효 기록
 * 가운데 기준 거리 이상인 첫 기록이 있는 회원이다. 탈퇴 회원은 뺀다. 공개 여부와 상관없이 운영자가 본다.
 */
@Service
public class EventEntrantService {

    private final EventRepository eventRepository;
    private final BattleHistoryService battleHistoryService;
    private final RunQueryService runQueryService;
    private final MemberQueryService memberQueryService;

    public EventEntrantService(
            EventRepository eventRepository,
            BattleHistoryService battleHistoryService,
            RunQueryService runQueryService,
            MemberQueryService memberQueryService) {
        this.eventRepository = eventRepository;
        this.battleHistoryService = battleHistoryService;
        this.runQueryService = runQueryService;
        this.memberQueryService = memberQueryService;
    }

    /**
     * 응모자 명단 한 쪽(회원 ID 내림차순, PageCursor). celebrityId를 주면 그 셀럽 기록을 이긴 회원만이다(COACH_CELEB 전용). 없는
     * 이벤트는 404 EVENT_NOT_FOUND, COACH_CELEB이 아니거나 설정에 없는 셀럽 ID면 IllegalArgumentException(400).
     */
    @Transactional(readOnly = true)
    public EventEntrants entrants(long eventId, Long celebrityId, PageCursor page) {
        Event event =
                eventRepository
                        .findById(eventId)
                        .orElseThrow(() -> new NotFoundRuleException(ReasonCode.EVENT_NOT_FOUND));
        EventConfig config = event.parsedConfig();
        Map<Long, List<EventEntry.Item>> byMember =
                switch (event.getKind()) {
                    case COACH_CELEB -> coachEntrants(event, config, celebrityId);
                    case RUN_DISTANCE -> runEntrants(event, config, celebrityId);
                };
        Map<Long, String> nicknames = memberQueryService.activeNicknames(byMember.keySet());
        List<EventEntrants.Entrant> all = new ArrayList<>();
        byMember.forEach(
                (memberId, items) -> {
                    String nickname = nicknames.get(memberId);
                    if (nickname != null) {
                        int done = (int) items.stream().filter(EventEntry.Item::done).count();
                        all.add(new EventEntrants.Entrant(memberId, nickname, done, items));
                    }
                });
        all.sort(Comparator.comparingLong(EventEntrants.Entrant::memberId).reversed());
        PageCursor.Slice<EventEntrants.Entrant> slice =
                page.slice(all, EventEntrants.Entrant::memberId);
        return new EventEntrants(
                event.getId(),
                event.getSlug(),
                event.getKind(),
                celebrityId,
                all.size(),
                slice.items(),
                slice.nextCursor());
    }

    private Map<Long, List<EventEntry.Item>> coachEntrants(
            Event event, EventConfig config, Long celebrityId) {
        List<Long> ids = config.celebrityIds();
        if (celebrityId != null && !ids.contains(celebrityId)) {
            throw new IllegalArgumentException("이벤트 셀럽이 아님: " + celebrityId);
        }
        Map<Long, Map<Long, LocalDateTime>> wins = new TreeMap<>();
        for (CelebrityWin w :
                battleHistoryService.celebrityWinners(
                        ids, event.getStartsAt(), event.getEndsAt())) {
            wins.computeIfAbsent(w.memberId(), m -> new HashMap<>())
                    .put(w.celebrityId(), w.firstWonAt());
        }
        Map<Long, String> names = battleHistoryService.celebrityNames(ids);
        Map<Long, List<EventEntry.Item>> result = new TreeMap<>();
        wins.forEach(
                (memberId, won) -> {
                    if (celebrityId == null || won.containsKey(celebrityId)) {
                        result.put(memberId, coachItems(ids, names, won));
                    }
                });
        return result;
    }

    private static List<EventEntry.Item> coachItems(
            List<Long> ids, Map<Long, String> names, Map<Long, LocalDateTime> won) {
        return ids.stream()
                .map(
                        id ->
                                new EventEntry.Item(
                                        EventService.celebrityKey(id),
                                        names.getOrDefault(id, "셀럽 " + id),
                                        won.containsKey(id),
                                        won.get(id),
                                        null))
                .toList();
    }

    private Map<Long, List<EventEntry.Item>> runEntrants(
            Event event, EventConfig config, Long celebrityId) {
        if (celebrityId != null) {
            throw new IllegalArgumentException("셀럽 거르기는 COACH_CELEB 전용");
        }
        long min = config.minDistanceMeters();
        String label = EventService.distanceLabel(min);
        Map<Long, List<EventEntry.Item>> result = new TreeMap<>();
        Map<Long, RunSummary> first =
                runQueryService.earliestValidRunsAtLeast(
                        min, event.getStartsAt(), event.getEndsAt());
        first.forEach(
                (memberId, run) ->
                        result.put(
                                memberId,
                                List.of(
                                        new EventEntry.Item(
                                                EventService.RUN_KEY_PREFIX + label,
                                                label,
                                                true,
                                                run.startedAt(),
                                                run.distance().meters()))));
        return result;
    }
}

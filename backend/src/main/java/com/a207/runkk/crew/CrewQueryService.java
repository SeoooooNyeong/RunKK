package com.a207.runkk.crew;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Week;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 조회 유스케이스(설계 6.1.7, srs.md 6.3 5.2·5.5). 다른 패키지 자료는 MemberQueryService·RunQueryService로만
 * 읽고(D-32), 공개 범위 규칙은 member의 DisclosureLevel 한 곳을 쓴다(D-52).
 */
@Service
@Transactional(readOnly = true)
public class CrewQueryService {

    /** 초대 코드 길이(InviteCode, FR-112). 길이가 다르면 저장소를 읽지 않고 INVALID_CODE다. */
    private static final int INVITE_CODE_LENGTH = 6;

    private final CrewRepository crewRepository;
    private final CrewAccess crewAccess;
    private final JoinRequestRepository joinRequestRepository;
    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final CrewRunHistory crewRunHistory;
    private final Clock clock;

    CrewQueryService(
            CrewRepository crewRepository,
            CrewAccess crewAccess,
            JoinRequestRepository joinRequestRepository,
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            CrewRunHistory crewRunHistory,
            Clock clock) {
        this.crewRepository = crewRepository;
        this.crewAccess = crewAccess;
        this.joinRequestRepository = joinRequestRepository;
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.crewRunHistory = crewRunHistory;
        this.clock = clock;
    }

    /**
     * 공개 크루만, 태그 AND, 키워드(이름·소개), 최근 생성순. 크루 밖 열람 범위 항목만 + 이번 주 크루 거리(W-16). 크루장 표시명은 그 페이지 크루만
     * MemberQueryService.summaries로 붙인다(B4 리뷰). v0.11에서 추천 결합과 태그 일치 수를 지웠다(FR-117, FR-118 삭제,
     * D-91). 같은 조건의 전체 수(totalCount)를 함께 준다(피그마 F-01d "검색 결과 [n]개", S15P31A207-295).
     */
    public CrewListView search(long viewerId, CrewSearch cond, PageCursor page) {
        PageCursor.Slice<CrewListRow> slice =
                page.slice(crewRepository.search(cond, page, thisWeek()), CrewListRow::crewId);
        List<CrewListRow> rows = slice.items();
        Map<Long, MemberSummary> leaders =
                memberQueryService.summaries(rows.stream().map(CrewListRow::leaderId).toList());
        List<CrewListView.Item> items =
                rows.stream()
                        .map(row -> new CrewListView.Item(row, nameOf(leaders, row.leaderId())))
                        .toList();
        return new CrewListView(items, slice.nextCursor(), crewRepository.count(cond));
    }

    /**
     * check(VIEW_CREW_INTERNAL). 표시명·티어는 MemberQueryService.summaries, 공개 수준은 disclosureOf로 받고
     * showsProfile()이 거짓인 크루원은 표시명·역할·크루 내 소개만 보인다(D-52). Runimal 유형은 web.crew가 disclosedMemberIds로
     * 붙인다(D-46).
     */
    public CrewMembersView members(long viewerId, long crewId) {
        Crew crew = crewAccess.check(viewerId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        List<CrewMembership> active = crew.activeMembers();
        List<Long> ids = crew.activeMemberIds();
        Map<Long, MemberSummary> summaries = memberQueryService.summaries(ids);
        Map<Long, DisclosureLevel> levels = memberQueryService.disclosureOf(viewerId, ids);
        Set<Long> disclosed =
                ids.stream()
                        .filter(
                                id ->
                                        levels.getOrDefault(id, DisclosureLevel.WITHDRAWN)
                                                .showsProfile())
                        .collect(Collectors.toSet());
        List<CrewMembersView.Line> lines = new ArrayList<>();
        for (CrewMembership membership : active) {
            boolean shown = disclosed.contains(membership.memberId());
            MemberSummary summary = summaries.get(membership.memberId());
            lines.add(
                    new CrewMembersView.Line(
                            membership.memberId(),
                            nameOf(summaries, membership.memberId()),
                            membership.role(),
                            membership.crewIntro(),
                            shown && summary != null ? summary.tier() : null));
        }
        return new CrewMembersView(crewId, lines, disclosed);
    }

    /** check(VIEW_OR_DECIDE_JOIN). 신청자 표시명·티어·신청일시(FR-136). */
    public PendingRequestsView pendingRequests(long requesterId, long crewId) {
        crewAccess.check(requesterId, crewId, CrewAction.VIEW_OR_DECIDE_JOIN);
        List<JoinRequest> pending = joinRequestRepository.findPending(crewId);
        Map<Long, MemberSummary> summaries =
                memberQueryService.summaries(pending.stream().map(JoinRequest::memberId).toList());
        return new PendingRequestsView(
                crewId,
                pending.stream()
                        .map(
                                r ->
                                        new PendingRequestsView.Line(
                                                r.getId(),
                                                r.memberId(),
                                                nameOf(summaries, r.memberId()),
                                                Optional.ofNullable(summaries.get(r.memberId()))
                                                        .map(MemberSummary::tier)
                                                        .orElse(null),
                                                r.requestedAt()))
                        .toList());
    }

    /**
     * check(VIEW_CREW_INTERNAL). 크루원 × RunQueryService.distancesByMember(크루원, 기간)로 크루원별 유효 거리 합.
     * 비공개 크루원도 순위와 거리만 보인다(FR-137). 같은 거리는 같은 순위다.
     */
    public CrewRankingView crewRanking(long viewerId, long crewId, AggregationPeriod p) {
        Crew crew = crewAccess.check(viewerId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        List<Long> ids = crew.activeMemberIds();
        Map<Long, Distance> distances =
                runQueryService.distancesByMember(ids, p.rangeOf(LocalDate.now(clock)));
        Map<Long, MemberSummary> summaries = memberQueryService.summaries(ids);
        List<Long> ordered =
                ids.stream()
                        .sorted(
                                Comparator.comparing(
                                                (Long id) ->
                                                        distances.getOrDefault(id, Distance.ZERO))
                                        .reversed()
                                        .thenComparing(Comparator.naturalOrder()))
                        .toList();
        List<CrewRankingView.Line> lines = new ArrayList<>();
        Distance previous = null;
        int rank = 0;
        for (int i = 0; i < ordered.size(); i++) {
            long id = ordered.get(i);
            Distance distance = distances.getOrDefault(id, Distance.ZERO);
            if (!distance.equals(previous)) {
                rank = i + 1;
                previous = distance;
            }
            lines.add(new CrewRankingView.Line(rank, id, nameOf(summaries, id), distance));
        }
        return new CrewRankingView(crewId, p, lines);
    }

    /**
     * 크루 한 곳(GET /crews/{id}). 공개 크루는 누구나, 비공개 크루는 크루원만 본다(비공개 크루는 목록·검색에 나오지 않음, Q-38). 초대 코드는
     * 크루장에게만 담는다.
     */
    public CrewDetailView detail(long viewerId, long crewId) {
        Crew crew = crewAccess.require(crewId);
        CrewRelation relation = crew.relationOf(viewerId);
        if (crew.visibility() != CrewVisibility.PUBLIC && relation == CrewRelation.OUTSIDER) {
            // 순번 ID로 비공개 크루의 존재를 가늠하지 못하게 없는 크루와 같은 응답을 준다(자가 리뷰 LOW)
            throw new BusinessRuleException(ReasonCode.CREW_NOT_FOUND);
        }
        return detailOf(crew, viewerId);
    }

    /**
     * 초대 코드로 찾은 크루의 요약(피그마 F-03c, S15P31A207-295). 가입시키지 않는다. 코드가 틀리거나 만료면 INVALID_CODE(가입과 같은 사유,
     * 대입 횟수 제한은 web.crew의 CrewJoinAttemptGuard가 앞뒤에서 건다). 비공개 크루도 준다(초대 코드가 비공개 크루의 가입 수단이다).
     */
    public InvitePreviewView invitePreview(String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        return Optional.of(normalized)
                .filter(c -> c.length() == INVITE_CODE_LENGTH)
                .flatMap(crewRepository::findByInviteCode)
                .filter(crew -> crew.acceptsInviteCode(normalized, now))
                .map(InvitePreviewView::of)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.INVALID_CODE));
    }

    /**
     * 본인 소속 크루(없으면 null)와 대기 중인 본인 가입 신청(GET /crews/me). 24시간 가입·생성 제한 중이면 풀리는 시각을 함께 준다(피그마 F-02c,
     * S15P31A207-295).
     */
    public MyCrewView myCrew(long memberId) {
        List<CrewMembership> history = crewRepository.findMembershipHistory(memberId);
        CrewDetailView crew =
                history.stream()
                        .filter(CrewMembership::isActive)
                        .map(CrewMembership::crewId)
                        .findFirst()
                        .flatMap(crewRepository::findById)
                        .map(c -> detailOf(c, memberId, history))
                        .orElse(null);
        List<JoinRequest> pending = joinRequestRepository.findPendingByMember(memberId);
        Map<Long, String> names = new HashMap<>();
        crewRepository
                .findAllById(pending.stream().map(JoinRequest::crewId).distinct().toList())
                .forEach(c -> names.put(c.getId(), c.name().value()));
        return new MyCrewView(
                crew,
                pending.stream()
                        .map(
                                r ->
                                        new MyCrewView.PendingJoin(
                                                r.getId(),
                                                r.crewId(),
                                                names.get(r.crewId()),
                                                r.requestedAt()))
                        .toList(),
                rejoinAvailableAt(history));
    }

    private CrewDetailView detailOf(Crew crew, long viewerId) {
        return detailOf(crew, viewerId, crewRepository.findMembershipHistory(viewerId));
    }

    /** 열람자의 멤버십 이력은 24시간 가입 제한 시각에만 쓴다. */
    private CrewDetailView detailOf(Crew crew, long viewerId, List<CrewMembership> viewerHistory) {
        long leaderId = crew.leaderId();
        Map<Long, MemberSummary> leader = memberQueryService.summaries(List.of(leaderId));
        CrewRole role = crew.membershipOf(viewerId).map(CrewMembership::role).orElse(null);
        return new CrewDetailView(
                crew.getId(),
                crew.name().value(),
                crew.intro(),
                crew.tags(),
                crew.visibility(),
                crew.joinPolicy(),
                crew.logoUrl(),
                leaderId,
                nameOf(leader, leaderId),
                crew.headcount(),
                crewRepository.weeklyDistance(crew.getId(), thisWeek()),
                crewRepository.weeklyDistance(crew.getId(), thisWeek().previous()),
                role,
                role == null ? null : crew.inviteCode(),
                crew.createdAt(),
                crewRepository.totalDistance(crew.getId()),
                crewRunHistory.finishedCount(crew.getId()),
                crewRunHistory.recent(crew.getId()),
                rejoinAvailableAt(viewerHistory));
    }

    /** 24시간 제한(COOLDOWN)이면 풀리는 시각, 소속 중이거나 제한이 없으면 null(JoinEligibility, FR-124). */
    private LocalDateTime rejoinAvailableAt(List<CrewMembership> history) {
        return JoinEligibility.evaluate(history, LocalDateTime.now(clock))
                .availableAt()
                .orElse(null);
    }

    private Week thisWeek() {
        return Week.of(LocalDateTime.now(clock));
    }

    private static DisplayName nameOf(Map<Long, MemberSummary> summaries, long memberId) {
        return Optional.ofNullable(summaries.get(memberId))
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }
}

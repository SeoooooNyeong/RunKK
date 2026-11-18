package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 일정 상세·수정·참석 취소·지난 일정 유스케이스(피그마 F-11b·F-11c·F-11d·F-13·F-13b·F-13d·F-13e, W6). 계약은
 * docs/sw/maintenance/2026-11-09_피그마맞춤-성/contract-W6.md. 숨김·삭제된 글과 크루런이 아닌 글은
 * POST_NOT_FOUND다(D-126).
 */
@Service
public class CrewScheduleService {

    private final CrewPostRepository crewPostRepository;
    private final CrewAccess crewAccess;
    private final CrewRunRepeater repeater;
    private final MemberQueryService memberQueryService;
    private final CrewRunHistory history;
    private final CrewTransactions transactions;
    private final CrewRunAnnouncer announcer;
    private final Clock clock;

    CrewScheduleService(
            CrewPostRepository crewPostRepository,
            CrewAccess crewAccess,
            CrewRunRepeater repeater,
            MemberQueryService memberQueryService,
            CrewRunHistory history,
            CrewTransactions transactions,
            CrewRunAnnouncer announcer,
            Clock clock) {
        this.crewPostRepository = crewPostRepository;
        this.crewAccess = crewAccess;
        this.repeater = repeater;
        this.memberQueryService = memberQueryService;
        this.history = history;
        this.transactions = transactions;
        this.announcer = announcer;
        this.clock = clock;
    }

    /** check(VIEW_CREW_INTERNAL) → 상세(제목·내용·거리·참석자·반복·수정·삭제 가능 여부). */
    @Transactional(readOnly = true)
    public CrewRunDetailView detail(long memberId, long postId) {
        CrewRunPost post = visible(postId);
        Crew crew = crewAccess.check(memberId, post.crewId(), CrewAction.VIEW_CREW_INTERNAL);
        return view(post, crew, memberId, LocalDateTime.now(clock));
    }

    /**
     * check(WRITE_POST) → 반복 행 → 글 행 순서로 잠가(회차 생성과 같은 순서, 코드 리뷰 M1) CrewRunPost.edit(작성자만,
     * VOTING·일시 전, 제목·내용·거리) → 반복 칸 반영(CrewRunRepeater.apply) → 바뀐 상세(F-13d·F-13e).
     */
    @Transactional
    public CrewRunDetailView edit(long memberId, long postId, CrewRunEdit edit) {
        CrewRunPost unlocked = visible(postId);
        Crew crew = crewAccess.check(memberId, unlocked.crewId(), CrewAction.WRITE_POST);
        repeater.lockSeriesBeforePost(unlocked);
        CrewRunPost post = locked(postId);
        LocalDateTime now = LocalDateTime.now(clock);
        post.edit(memberId, edit, now);
        announcer.scheduleChanged(post); // 열린 크루런 목록 신호(설계 05-32 SCHEDULE)
        crewPostRepository.save(post);
        repeater.apply(post, edit.repeat(), now);
        return view(post, crew, memberId, now);
    }

    /**
     * 참석 취소(F-13b): check(RESPOND) → 글 행을 잠가 CrewRunPost.withdraw(응답을 거둔다). 크루런이 아닌 일정(대면 모임)은
     * TARGET_INVALID. 낙관적 잠금 충돌은 최대 3회 다시 한다(참석 응답과 같다).
     */
    public AttendanceView withdrawAttendance(long memberId, long postId) {
        return transactions.retrying(
                () -> {
                    CrewPost found = visiblePost(postId);
                    crewAccess.check(memberId, found.crewId(), CrewAction.RESPOND);
                    if (!(found instanceof CrewRunPost)) {
                        throw new BusinessRuleException(ReasonCode.TARGET_INVALID);
                    }
                    CrewRunPost post = locked(postId);
                    AttendanceView view = post.withdraw(memberId, LocalDateTime.now(clock));
                    announcer.scheduleChanged(post);
                    crewPostRepository.save(post);
                    return view;
                });
    }

    /**
     * check(VIEW_CREW_INTERNAL) → 그 달 지난 크루런(FINISHED·CANCELLED) 일시 최신순, 날짜 목록, 가장 이른 월. date를 주면
     * runs만 그날로 거른다. date가 month 밖이면 INVALID_YEAR_MONTH(F-11b·F-11c, W7 달력).
     */
    @Transactional(readOnly = true)
    public PastCrewRunsView past(long memberId, long crewId, YearMonth month, LocalDate date) {
        crewAccess.check(memberId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        if (date != null && !YearMonth.from(date).equals(month)) {
            throw new BusinessRuleException(ReasonCode.INVALID_YEAR_MONTH);
        }
        List<CrewRunPost> runs =
                crewPostRepository.findPastCrewRuns(
                        crewId,
                        month.atDay(1).atStartOfDay(),
                        month.plusMonths(1).atDay(1).atStartOfDay());
        List<LocalDate> dates =
                runs.stream().map(p -> p.scheduledAt().toLocalDate()).distinct().sorted().toList();
        List<PastCrewRunsView.Run> lines =
                runs.stream()
                        .filter(p -> date == null || p.scheduledAt().toLocalDate().equals(date))
                        .map(CrewScheduleService::pastLine)
                        .toList();
        YearMonth earliest =
                crewPostRepository
                        .findEarliestPastCrewRun(crewId)
                        .map(YearMonth::from)
                        .orElse(null);
        return new PastCrewRunsView(month, dates, lines, earliest);
    }

    private static PastCrewRunsView.Run pastLine(CrewRunPost p) {
        int runners =
                (int) p.participations().stream().filter(CrewRunParticipation::isRunner).count();
        return new PastCrewRunsView.Run(
                p.getId(),
                p.title(),
                p.scheduledAt(),
                p.distance(),
                p.status(),
                p.counts().going(),
                runners);
    }

    private CrewRunDetailView view(CrewRunPost post, Crew crew, long viewerId, LocalDateTime now) {
        List<CrewRunParticipation> going = attendeesInOrder(post);
        List<Long> ids =
                Stream.concat(
                                Stream.ofNullable(post.authorId()),
                                going.stream().flatMap(p -> p.memberId().stream()))
                        .distinct()
                        .toList();
        Map<Long, MemberSummary> names = memberQueryService.summaries(ids);
        Map<Long, Gender> genders = memberQueryService.shownGendersOf(ids);
        Function<CrewRunParticipation, Optional<Distance>> distances =
                history.attendeeDistances(post);
        List<CrewRunDetailView.Attendee> attendees =
                going.stream()
                        .map(p -> attendee(p, post, viewerId, names, genders, distances))
                        .toList();
        Optional<CrewRunSeries> series = repeater.repeatingSeriesOf(post, now);
        boolean mine = Objects.equals(post.authorId(), viewerId);
        return new CrewRunDetailView(
                post.getId(),
                post.crewId(),
                post.title(),
                post.body(),
                post.authorId(),
                nameOf(names, post.authorId()),
                mine,
                post.createdAt(),
                post.scheduledAt(),
                post.voteDeadline(),
                post.distance(),
                post.status(),
                attendees.size(),
                post.participationOf(viewerId).map(CrewRunParticipation::answer).orElse(null),
                post.canEdit(viewerId, now),
                mine || crewAccess.allows(crew, viewerId, CrewAction.DELETE_ANY_CONTENT),
                series.isPresent() ? CrewRunRepeat.WEEKLY : CrewRunRepeat.NONE,
                series.map(CrewRunSeries::dayOfWeek).orElse(null),
                attendees,
                post.myRunOf(viewerId));
    }

    private static CrewRunDetailView.Attendee attendee(
            CrewRunParticipation p,
            CrewRunPost post,
            long viewerId,
            Map<Long, MemberSummary> names,
            Map<Long, Gender> genders,
            Function<CrewRunParticipation, Optional<Distance>> distances) {
        Long id = p.memberId().orElse(null);
        Gender gender = id == null ? null : genders.get(id);
        return new CrewRunDetailView.Attendee(
                id,
                nameOf(names, id),
                id != null && id.equals(post.authorId()),
                id != null && id == viewerId,
                gender == null ? null : gender.name(),
                distances.apply(p).orElse(null));
    }

    /** 참석(GOING) 참여: 작성자가 참석 중이면 맨 앞, 나머지는 응답 순(참여 ID 순, 안정 정렬). */
    private static List<CrewRunParticipation> attendeesInOrder(CrewRunPost post) {
        List<CrewRunParticipation> going =
                new ArrayList<>(
                        post.participations().stream()
                                .filter(p -> p.answer() == AttendanceAnswer.GOING)
                                .toList());
        going.sort(
                Comparator.comparing(
                        (CrewRunParticipation p) ->
                                !p.memberId().map(id -> id.equals(post.authorId())).orElse(false)));
        return going;
    }

    private static DisplayName nameOf(Map<Long, MemberSummary> names, Long memberId) {
        return Optional.ofNullable(memberId)
                .map(names::get)
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }

    private CrewPost visiblePost(long postId) {
        return crewPostRepository
                .findById(postId)
                .filter(CrewPost::isVisible)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
    }

    private CrewRunPost visible(long postId) {
        if (visiblePost(postId) instanceof CrewRunPost post) {
            return post;
        }
        throw new BusinessRuleException(ReasonCode.POST_NOT_FOUND);
    }

    /** 글 행을 잠가 최신 상태로 읽는다(참석 응답·시계·삭제와 줄을 세운다). */
    private CrewRunPost locked(long postId) {
        if (crewPostRepository.findByIdForUpdate(postId).filter(CrewPost::isVisible).orElse(null)
                instanceof CrewRunPost post) {
            return post;
        }
        throw new BusinessRuleException(ReasonCode.POST_NOT_FOUND);
    }
}

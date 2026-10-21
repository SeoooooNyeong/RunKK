package com.a207.runkk.crew;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 게시글 유스케이스(설계 6.1.7, srs.md 6.3 5.4). 응원글·모집글 작성·조회·참여 투표·삭제. 크루장이 남의 글을 지우면 감사 대상이다(FR-130).
 */
@Service
public class CrewPostService {

    private static final String RECRUIT_TEXT = "크루런 모집 · 참여 투표가 열렸어요";

    private final CrewPostRepository crewPostRepository;
    private final CrewAccess crewAccess;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MemberQueryService memberQueryService;
    private final WeeklyRaceRepository weeklyRaceRepository;
    private final Clock clock;

    CrewPostService(
            CrewPostRepository crewPostRepository,
            CrewAccess crewAccess,
            NotificationService notificationService,
            AuditService auditService,
            MemberQueryService memberQueryService,
            WeeklyRaceRepository weeklyRaceRepository,
            Clock clock) {
        this.crewPostRepository = crewPostRepository;
        this.crewAccess = crewAccess;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.memberQueryService = memberQueryService;
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.clock = clock;
    }

    /** check(WRITE_POST) → new CheerPost(FR-129). */
    @Transactional
    public PostResult writeCheer(long memberId, long crewId, String body) {
        crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        CrewPost post =
                crewPostRepository.save(
                        new CheerPost(crewId, memberId, body, LocalDateTime.now(clock)));
        return new PostResult(post.getId());
    }

    /** check(WRITE_POST) → new CrewRunPost → 작성자를 뺀 크루원에게 알림(CREW_RUN_RECRUIT)(FR-132, FR-193). */
    @Transactional
    public PostResult writeCrewRun(long memberId, long crewId, CrewRunDraft draft) {
        Crew crew = crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        CrewPost post =
                crewPostRepository.save(
                        new CrewRunPost(crewId, memberId, draft, LocalDateTime.now(clock)));
        NavigationTarget target =
                new NavigationTarget(Screen.CREW_RUN_RECRUIT, String.valueOf(post.getId()));
        crew.activeMemberIds().stream()
                .filter(id -> id != memberId)
                .forEach(
                        id ->
                                notificationService.notify(
                                        new NotificationRequest(
                                                id,
                                                NotificationType.CREW_RUN_RECRUIT,
                                                RECRUIT_TEXT,
                                                target)));
        return new PostResult(post.getId());
    }

    /**
     * check(VIEW_CREW_INTERNAL) → 보이는 글 최신순 summary. kind가 null이면 두 종류 모두. 지난 크루런은 크루 주간 거리에 반영된 거리
     * 합을 붙인다(5.4 규칙 2).
     */
    @Transactional(readOnly = true)
    public PostListView list(long memberId, long crewId, PostKind kind) {
        return list(memberId, crewId, kind, PageCursor.all());
    }

    /** 페이지 나누기(B4 리뷰). DB에서 cursor 뒤 size + 1개만 읽는다. */
    @Transactional(readOnly = true)
    public PostListView list(long memberId, long crewId, PostKind kind, PageCursor page) {
        crewAccess.check(memberId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        PageCursor.Slice<CrewPost> slice =
                page.slice(crewPostRepository.findVisible(crewId, kind, page), CrewPost::getId);
        List<PostSummary> summaries =
                slice.items().stream().map(post -> post.summary(memberId)).toList();
        Map<Long, MemberSummary> authors =
                memberQueryService.summaries(
                        summaries.stream()
                                .map(PostSummary::authorId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList());
        return new PostListView(
                summaries.stream()
                        .map(s -> new PostListView.Line(s, authorName(authors, s), contribution(s)))
                        .toList(),
                slice.nextCursor());
    }

    private static DisplayName authorName(Map<Long, MemberSummary> authors, PostSummary s) {
        return Optional.ofNullable(s.authorId())
                .map(authors::get)
                .map(MemberSummary::displayName)
                .orElse(DisplayName.WITHDRAWN);
    }

    private Distance contribution(PostSummary s) {
        if (s.crewRun() == null || s.crewRun().status() != CrewRunStatus.FINISHED) {
            return null;
        }
        return s.crewRun().linkedRecordIds().stream()
                .map(weeklyRaceRepository::contributionOf)
                .reduce(Distance.ZERO, Distance::plus);
    }

    /** check(VOTE_OR_JOIN_CREW_RUN) → CrewRunPost.vote(FR-145). */
    @Transactional
    public PostResult vote(long memberId, long postId, VoteAnswer answer) {
        CrewRunPost post =
                crewPostRepository
                        .findCrewRunByIdForUpdate(postId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
        crewAccess.check(memberId, post.crewId(), CrewAction.VOTE_OR_JOIN_CREW_RUN);
        post.vote(memberId, answer, LocalDateTime.now(clock));
        crewPostRepository.save(post);
        return new PostResult(postId);
    }

    /** CrewPost.delete → FORCED면 감사(게시글강제삭제)(FR-130, FR-134). */
    @Transactional
    public PostResult delete(long requesterId, long postId) {
        CrewPost post =
                crewPostRepository
                        .findById(postId)
                        .filter(CrewPost::isVisible)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
        Crew crew = crewAccess.require(post.crewId());
        DeletionKind kind = post.delete(requesterId, crew.relationOf(requesterId));
        crewPostRepository.save(post);
        if (kind == DeletionKind.FORCED) {
            auditService.record(
                    new AuditEntry(
                            String.valueOf(requesterId),
                            ActorRole.CREW_LEADER,
                            AuditAction.POST_FORCE_DELETE,
                            String.valueOf(postId),
                            String.valueOf(post.authorId()),
                            null));
        }
        return new PostResult(postId);
    }
}

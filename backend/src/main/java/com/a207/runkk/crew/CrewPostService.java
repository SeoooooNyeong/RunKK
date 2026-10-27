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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 게시글 유스케이스(설계 6.1.7, srs.md 6.3 5.4, D-99, D-100, D-106): 응원·크루런·대면 모임·공지·투표·게시글 작성, 목록, 참석 응답,
 * 공지 고정, 투표 응답·결과, 앨범·사진, 삭제, 신고 숨김, 투표 마감 알림. 크루장·운영진이 남의 글을 지우면 감사 대상이다(FR-213). 숨긴 글은
 * 목록·응답·삭제·사진에서 없는 글과 같다(POST_NOT_FOUND, D-126, D-154).
 */
@Service
public class CrewPostService {

    /** 앨범 기본 페이지 크기(FR-212). */
    static final int ALBUM_PAGE = 30;

    /** 투표 마감 임박 알림 창(제안, D-113). */
    static final Duration POLL_REMINDER_WINDOW = Duration.ofHours(1);

    /** 신고 미리보기 길이(D-135). */
    static final int PREVIEW_LENGTH = 100;

    private static final String RECRUIT_TEXT = "크루런 모집 · 참석 응답이 열렸어요";

    private final CrewPostRepository crewPostRepository;
    private final CrewAccess crewAccess;
    private final PostPhotoStore postPhotoStore;
    private final CrewRunService crewRunService;
    private final CrewNewsNotifier newsNotifier;
    private final NotificationService notificationService;
    private final MemberQueryService memberQueryService;
    private final WeeklyRaceRepository weeklyRaceRepository;
    private final AuditService auditService;
    private final CrewTransactions transactions;
    private final Clock clock;

    CrewPostService(
            CrewPostRepository crewPostRepository,
            CrewAccess crewAccess,
            PostPhotoStore postPhotoStore,
            CrewRunService crewRunService,
            CrewNewsNotifier newsNotifier,
            NotificationService notificationService,
            MemberQueryService memberQueryService,
            WeeklyRaceRepository weeklyRaceRepository,
            AuditService auditService,
            CrewTransactions transactions,
            Clock clock) {
        this.crewPostRepository = crewPostRepository;
        this.crewAccess = crewAccess;
        this.postPhotoStore = postPhotoStore;
        this.crewRunService = crewRunService;
        this.newsNotifier = newsNotifier;
        this.notificationService = notificationService;
        this.memberQueryService = memberQueryService;
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.auditService = auditService;
        this.transactions = transactions;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** check(WRITE_POST) → new CheerPost(FR-129). 알림 없음. */
    @Transactional
    public PostResult writeCheer(long memberId, long crewId, String body) {
        crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        return saved(new CheerPost(crewId, memberId, body, now()));
    }

    /**
     * check(WRITE_POST) → new CrewRunPost(작성자 GOING) → 작성자를 뺀 크루원에게 CREW_RUN_RECRUIT(FR-132,
     * FR-193).
     */
    @Transactional
    public PostResult writeCrewRun(long memberId, long crewId, CrewRunDraft draft) {
        Crew crew = crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        CrewPost post = crewPostRepository.save(new CrewRunPost(crewId, memberId, draft, now()));
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

    /** check(WRITE_POST) → new MeetupPost(작성자 GOING, D-151) → CREW_SCHEDULE(FR-132, FR-218). */
    @Transactional
    public PostResult writeMeetup(long memberId, long crewId, MeetupDraft draft) {
        crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        return announced(new MeetupPost(crewId, memberId, draft, now()), memberId);
    }

    /**
     * 고정이면 crew 행 잠금(checkForUpdate(MANAGE_NOTICE)) 안에서 보이는 고정 공지가 이미 MAX_PINNED개면 PIN_LIMIT, 아니면
     * check(MANAGE_NOTICE). 크루원이면 403 → new NoticePost → CREW_NOTICE(FR-205, FR-218).
     */
    @Transactional
    public PostResult writeNotice(long memberId, long crewId, NoticeDraft draft) {
        if (draft.pinned()) {
            crewAccess.checkForUpdate(memberId, crewId, CrewAction.MANAGE_NOTICE);
            requirePinRoom(crewId);
        } else {
            crewAccess.check(memberId, crewId, CrewAction.MANAGE_NOTICE);
        }
        return announced(new NoticePost(crewId, memberId, draft, now()), memberId);
    }

    /** check(WRITE_POST) → new PollPost → CREW_POLL(FR-206, FR-218). */
    @Transactional
    public PostResult writePoll(long memberId, long crewId, PollDraft draft) {
        crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        return announced(new PollPost(crewId, memberId, draft, now()), memberId);
    }

    /**
     * check(WRITE_POST) → new BoardPost → 한 트랜잭션에서 crew_post 저장 + 사진마다 PostPhoto 메타
     * 저장·PostPhotoStore.save(위치 메타데이터를 지운 바이트, D-142). 알림 없음(FR-211, D-100).
     */
    @Transactional
    public PostResult writeBoard(long memberId, long crewId, BoardDraft draft) {
        crewAccess.check(memberId, crewId, CrewAction.WRITE_POST);
        BoardPost post = new BoardPost(crewId, memberId, draft, now());
        crewPostRepository.save(post);
        List<PostPhoto> photos = post.photos();
        for (int i = 0; i < photos.size(); i++) {
            postPhotoStore.save(photos.get(i).getId(), draft.photos().get(i).jpeg());
        }
        return new PostResult(post.getId());
    }

    private PostResult saved(CrewPost post) {
        return new PostResult(crewPostRepository.save(post).getId());
    }

    private PostResult announced(CrewPost post, long authorId) {
        CrewPost saved = crewPostRepository.save(post);
        newsNotifier.announce(saved, authorId);
        return new PostResult(saved.getId());
    }

    private void requirePinRoom(long crewId) {
        if (crewPostRepository.countPinnedNotices(crewId) >= NoticePost.MAX_PINNED) {
            throw new BusinessRuleException(ReasonCode.PIN_LIMIT);
        }
    }

    /**
     * check(VIEW_CREW_INTERNAL) → 보이는 글(삭제·숨김 제외, 종류 필터, pinnedOnly면 고정 공지만) 최신순 summary +
     * canDelete(본인 글 또는 열람자 L·M). 크기를 주지 않으면 한 번에 PageCursor.MAX_SIZE(100)개까지 읽고 nextCursor를 준다(전체
     * 읽기 금지, 리뷰 HIGH). 지난 크루런은 크루 주간 거리에 반영된 거리 합을 붙인다(FR-131, FR-133, FR-212, FR-213).
     */
    @Transactional(readOnly = true)
    public PostListView list(long memberId, long crewId, PostQuery query) {
        Crew crew = crewAccess.check(memberId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        boolean deletesAny = crewAccess.allows(crew, memberId, CrewAction.DELETE_ANY_CONTENT);
        PostQuery bounded =
                query.page().size() == null
                        ? new PostQuery(
                                query.kinds(),
                                query.pinnedOnly(),
                                new PageCursor(query.page().cursor(), PageCursor.MAX_SIZE))
                        : query;
        PageCursor.Slice<CrewPost> slice =
                bounded.page()
                        .slice(crewPostRepository.findVisible(crewId, bounded), CrewPost::getId);
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
                        .map(
                                s ->
                                        new PostListView.Line(
                                                s,
                                                nameOf(authors, s.authorId()),
                                                contribution(s),
                                                s.mine() || deletesAny))
                        .toList(),
                slice.nextCursor());
    }

    private static DisplayName nameOf(Map<Long, MemberSummary> names, Long memberId) {
        return Optional.ofNullable(memberId)
                .map(names::get)
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

    /**
     * 글 없음·삭제·숨김 → POST_NOT_FOUND → check(RESPOND)(잠그기 전에 구성원부터 본다) → 일정(SchedulePost)이 아니면
     * TARGET_INVALID → SchedulePost.answer(다형성: 크루런 참가 규칙 / 대면 모임 정원). 글 행을 잠가 최신 상태로 읽고, 낙관적 잠금
     * 충돌은 최대 3회 다시 하고 다 쓰면 CONCURRENT_UPDATE(FR-145, D-99, D-154).
     */
    public AttendanceView answerAttendance(long memberId, long postId, AttendanceAnswer answer) {
        return transactions.retrying(
                () -> {
                    crewAccess.check(memberId, visible(postId).crewId(), CrewAction.RESPOND);
                    CrewPost post = visibleForUpdate(postId);
                    if (!(post instanceof SchedulePost schedule)) {
                        throw new BusinessRuleException(ReasonCode.TARGET_INVALID);
                    }
                    AttendanceView view = schedule.answer(memberId, answer, now());
                    crewPostRepository.save(post);
                    return view;
                });
    }

    /**
     * crew 행 잠금(checkForUpdate(MANAGE_NOTICE)) → 공지가 아니면 TARGET_INVALID → 고정이면 이미 고정된 공지는 변화 없이 참,
     * 아니면 보이는 고정 공지가 MAX_PINNED개일 때 PIN_LIMIT → NoticePost.pin·unpin. 바뀐 뒤 고정 여부를 돌려준다(FR-205,
     * D-154, D-165).
     */
    @Transactional
    public boolean pin(long memberId, long postId, boolean pinned) {
        long crewId = visible(postId).crewId();
        crewAccess.checkForUpdate(memberId, crewId, CrewAction.MANAGE_NOTICE);
        CrewPost post = visibleForUpdate(postId); // 크루 잠금을 기다린 사이의 고정·숨김을 본다(크루 → 글 순서로 잠근다)
        if (!(post instanceof NoticePost notice)) {
            throw new BusinessRuleException(ReasonCode.TARGET_INVALID);
        }
        if (!pinned) {
            notice.unpin();
        } else if (!notice.isPinned()) {
            requirePinRoom(post.crewId());
            notice.pin();
        }
        crewPostRepository.save(notice);
        return notice.isPinned();
    }

    /** check(VIEW_CREW_INTERNAL) → 선택지별 수·응답자 표시명(익명 아님)·본인 선택(FR-207). */
    @Transactional(readOnly = true)
    public PollResultView pollResult(long memberId, long postId) {
        PollPost poll = visiblePoll(postId);
        crewAccess.check(memberId, poll.crewId(), CrewAction.VIEW_CREW_INTERNAL);
        return resultOf(poll, memberId);
    }

    /**
     * check(RESPOND) → 글 행을 잠가 PollPost.respond(memberId, optionNo, now) → pollResult(FR-207). 같은
     * 회원의 연속 응답(두 번 누름)은 잠금으로 줄을 세워 마지막 응답이 남고, 낙관적 잠금 충돌은 최대 3회 다시 한다.
     */
    public PollResultView respondPoll(long memberId, long postId, int optionNo) {
        return transactions.retrying(
                () -> {
                    crewAccess.check(memberId, visiblePoll(postId).crewId(), CrewAction.RESPOND);
                    if (!(visibleForUpdate(postId) instanceof PollPost poll)) {
                        throw new BusinessRuleException(ReasonCode.TARGET_INVALID);
                    }
                    poll.respond(memberId, optionNo, now());
                    crewPostRepository.save(poll);
                    return resultOf(poll, memberId);
                });
    }

    private PollPost visiblePoll(long postId) {
        if (visible(postId) instanceof PollPost poll) {
            return poll;
        }
        throw new BusinessRuleException(ReasonCode.TARGET_INVALID);
    }

    private PollResultView resultOf(PollPost poll, long viewerId) {
        Map<Long, MemberSummary> names =
                memberQueryService.summaries(
                        poll.responses().stream().map(PollResponse::memberId).toList());
        List<PollResultView.Option> options =
                poll.options().stream()
                        .map(
                                o -> {
                                    List<PollResultView.Respondent> respondents =
                                            poll.responses().stream()
                                                    .filter(r -> r.optionNo() == o.optionNo())
                                                    .map(
                                                            r ->
                                                                    new PollResultView.Respondent(
                                                                            r.memberId(),
                                                                            nameOf(
                                                                                    names,
                                                                                    r.memberId())))
                                                    .toList();
                                    return new PollResultView.Option(
                                            o.optionNo(),
                                            o.label(),
                                            respondents.size(),
                                            respondents);
                                })
                        .toList();
        return new PollResultView(
                poll.getId(),
                poll.body(),
                poll.method(),
                poll.closesAt(),
                poll.isClosed(now()),
                options,
                poll.responseOf(viewerId).map(PollResponse::optionNo).orElse(null));
    }

    /** check(VIEW_CREW_INTERNAL) → 보이는 게시글의 사진 최신순(photoId 커서, 기본 30장, FR-212). */
    @Transactional(readOnly = true)
    public AlbumView album(long memberId, long crewId, PageCursor page) {
        crewAccess.check(memberId, crewId, CrewAction.VIEW_CREW_INTERNAL);
        PageCursor sized = page.size() == null ? new PageCursor(page.cursor(), ALBUM_PAGE) : page;
        PageCursor.Slice<PostPhoto> slice =
                sized.slice(crewPostRepository.findAlbum(crewId, sized), PostPhoto::getId);
        return new AlbumView(
                slice.items().stream()
                        .map(p -> new AlbumView.Photo(p.getId(), p.postId(), p.createdAt()))
                        .toList(),
                slice.nextCursor());
    }

    /**
     * 사진·글이 없거나 글이 삭제·숨김이면 POST_NOT_FOUND → 사진이 속한 크루의 check(VIEW_CREW_INTERNAL)(현재 구성원만, 아니면 403)
     * → PostPhotoStore.load(FR-212, D-100).
     */
    @Transactional(readOnly = true)
    public PhotoContent photo(long memberId, long photoId) {
        PostPhoto photo =
                crewPostRepository
                        .findPhoto(photoId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
        CrewPost post = visible(photo.postId());
        crewAccess.check(memberId, post.crewId(), CrewAction.VIEW_CREW_INTERNAL);
        byte[] jpeg =
                postPhotoStore
                        .load(photoId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
        return new PhotoContent(photoId, jpeg);
    }

    /**
     * 글 없음·삭제·숨김 → POST_NOT_FOUND(숨김 중 삭제는 받지 않는다, D-126) → CrewPost.delete(본인 또는 L·M) → 크루런이면
     * CrewRunService.endByDeletion(D-66) → 게시글이면 PostPhotoStore.deleteByPost → FORCED면
     * 감사(POST_FORCE_DELETE, 행위자 역할 CREW_LEADER 또는 CREW_MANAGER)(FR-130, FR-134, FR-213).
     */
    @Transactional
    public PostResult delete(long requesterId, long postId) {
        CrewPost post = visibleForUpdate(postId); // 크루런 시계·참석 응답과 줄을 세운다
        Crew crew = crewAccess.require(post.crewId());
        CrewRelation relation = crew.relationOf(requesterId);
        DeletionKind kind = post.delete(requesterId, relation);
        crewPostRepository.save(post);
        if (post instanceof CrewRunPost run) {
            crewRunService.endByDeletion(run);
        }
        if (post instanceof BoardPost) {
            postPhotoStore.deleteByPost(postId);
        }
        if (kind == DeletionKind.FORCED) {
            auditService.record(
                    new AuditEntry(
                            String.valueOf(requesterId),
                            relation == CrewRelation.MANAGER
                                    ? ActorRole.CREW_MANAGER
                                    : ActorRole.CREW_LEADER,
                            AuditAction.POST_FORCE_DELETE,
                            String.valueOf(postId),
                            post.authorId() == null ? null : String.valueOf(post.authorId()),
                            null));
        }
        return new PostResult(postId);
    }

    /** 신고용 조회(D-135). 글이 없거나 삭제됐으면 빈 값, 숨김이어도 돌려준다. report.target의 CrewPostReportAdapter만 부른다. */
    @Transactional(readOnly = true)
    public Optional<ReportedPostView> reportedView(long postId) {
        return crewPostRepository
                .findById(postId)
                .filter(post -> !post.isDeleted())
                .map(
                        post ->
                                new ReportedPostView(
                                        post.getId(),
                                        post.crewId(),
                                        post.kind(),
                                        post.authorId(),
                                        preview(post.body()),
                                        post.isHidden()));
    }

    /** 앞 100자(코드 포인트 단위, 서로게이트 쌍을 가르지 않는다). */
    private static String preview(String body) {
        if (body.codePointCount(0, body.length()) <= PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, body.offsetByCodePoints(0, PREVIEW_LENGTH));
    }

    /** 신고 숨김(FR-215). 신고 접수 트랜잭션 안(자동 숨김)이나 운영자 HIDE가 report.target을 거쳐 부른다. 없거나 삭제된 글은 그대로 둔다. */
    @Transactional
    public void hideReported(long postId) {
        crewPostRepository
                .findByIdForUpdate(postId)
                .filter(post -> !post.isDeleted())
                .ifPresent(
                        post -> {
                            post.hide();
                            crewPostRepository.save(post);
                        });
    }

    /**
     * 신고 숨김 해제(FR-216, 운영자 기각). 숨긴 고정 공지를 다시 보일 때 보이는 고정 공지가 이미 MAX_PINNED개면 고정을 풀고 보인다(crew 행 잠금
     * 안, D-154).
     */
    @Transactional
    public void unhideReported(long postId) {
        crewPostRepository
                .findById(postId)
                .filter(post -> !post.isDeleted() && post.isHidden())
                .ifPresent(
                        post -> {
                            if (post instanceof NoticePost notice && notice.isPinned()) {
                                crewAccess.requireForUpdate(post.crewId());
                                if (crewPostRepository.countPinnedNotices(post.crewId())
                                        >= NoticePost.MAX_PINNED) {
                                    notice.unpin();
                                }
                            }
                            post.unhide();
                            crewPostRepository.save(post);
                        });
    }

    /**
     * 마감이 now ~ now + 1시간이고 아직 알리지 않은 보이는 투표마다 새 트랜잭션에서: 현재 크루원 중 미응답자에게 POLL_CLOSING_SOON →
     * markReminded. 한 투표의 실패(다른 실행이 먼저 표시한 낙관적 잠금 충돌 등)는 그 투표만 건너뛴다. 알린 투표 수를 돌려준다. batch.crew
     * PollReminderJob이 1분마다 부른다(FR-218, D-106, D-125).
     */
    public int remindClosingPolls(LocalDateTime now) {
        LocalDateTime until = now.plus(POLL_REMINDER_WINDOW);
        List<Long> due =
                transactions.read(
                        () ->
                                crewPostRepository.findClosingPolls(now, until).stream()
                                        .map(CrewPost::getId)
                                        .toList());
        int reminded = 0;
        for (long pollId : due) {
            if (transactions.once("poll_reminder", () -> remind(pollId, now, until), false)) {
                reminded++;
            }
        }
        return reminded;
    }

    private boolean remind(long pollId, LocalDateTime now, LocalDateTime until) {
        Optional<CrewPost> found = crewPostRepository.findById(pollId);
        if (found.isEmpty()
                || !(found.get() instanceof PollPost poll)
                || !poll.isVisible()
                || poll.isReminded()
                || poll.closesAt().isBefore(now)
                || poll.closesAt().isAfter(until)) {
            return false;
        }
        Collection<Long> recipients =
                poll.nonRespondents(crewAccess.require(poll.crewId()).activeMemberIds());
        newsNotifier.remindClosing(poll, recipients);
        poll.markReminded();
        crewPostRepository.save(poll);
        return true;
    }

    private CrewPost visible(long postId) {
        return crewPostRepository
                .findById(postId)
                .filter(CrewPost::isVisible)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
    }

    private CrewPost visibleForUpdate(long postId) {
        return crewPostRepository
                .findByIdForUpdate(postId)
                .filter(CrewPost::isVisible)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
    }
}

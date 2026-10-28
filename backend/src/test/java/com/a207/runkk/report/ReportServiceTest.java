package com.a207.runkk.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

/**
 * 설계 6.1.12a ReportService, 6.2 A-24(D-104, D-115, D-138, D-149). TC-6122~TC-6127(submit 기초 경로
 * R1~R8), TC-6130~TC-6132(hide·dismiss), TC-6136(cases), TC-6137(탈퇴).
 */
@DisplayName("report ReportService")
class ReportServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 23, 9, 0);
    private static final long CREW = 7;
    private static final long AUTHOR = 3;
    private static final long POST = 100;
    private static final long MESSAGE = 501;
    private static final Set<Long> MEMBERS = Set.of(1L, 2L, 3L, 4L, 5L, 6L);
    private static final String OP = "KAKAO:op-1";

    private final InMemoryReportCaseRepository repository = new InMemoryReportCaseRepository();
    private final FakeReportedContentPort posts =
            new FakeReportedContentPort(
                    ReportTargetKind.CHEER,
                    ReportTargetKind.SCHEDULE,
                    ReportTargetKind.NOTICE,
                    ReportTargetKind.POLL,
                    ReportTargetKind.BOARD);
    private final FakeReportedContentPort chats =
            new FakeReportedContentPort(ReportTargetKind.CHAT_MESSAGE);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);
    private MutableClock clock;
    private ReportService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(N.atZone(SEOUL).toInstant(), SEOUL);
        service = new ReportService(repository, List.of(posts, chats), notifications, audit, clock);
        posts.put(ReportTargetKind.CHEER, POST, content("응원합니다", false));
        chats.put(ReportTargetKind.CHAT_MESSAGE, MESSAGE, content("채팅", false));
    }

    private static ReportedContent content(String preview, boolean hidden) {
        return new ReportedContent(CREW, AUTHOR, preview, hidden, MEMBERS);
    }

    private static ReportCommand cheer(ReportReason reason) {
        return new ReportCommand(ReportTargetKind.CHEER, POST, reason, null);
    }

    private static void rejects(Executable call, ReasonCode reason) {
        BusinessRuleException e =
                org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, call);
        assertThat(e.reason()).isEqualTo(reason);
    }

    private ReportCase onlyCase() {
        assertThat(repository.cases).hasSize(1);
        return repository.cases.values().iterator().next();
    }

    @Test
    @DisplayName("TC-6122: 없음·삭제·종류 불일치·숨김은 REPORT_TARGET_NOT_FOUND이고 사례·신고 0행(R1·R2)")
    void submit_missingOrHiddenTarget() {
        rejects(
                () ->
                        service.submit(
                                4,
                                new ReportCommand(
                                        ReportTargetKind.CHEER, 999, ReportReason.ABUSE, null)),
                ReasonCode.REPORT_TARGET_NOT_FOUND);
        rejects(
                () ->
                        service.submit(
                                4,
                                new ReportCommand(
                                        ReportTargetKind.BOARD, POST, ReportReason.ABUSE, null)),
                ReasonCode.REPORT_TARGET_NOT_FOUND);
        posts.put(ReportTargetKind.CHEER, POST, content("숨김", true));
        rejects(
                () -> service.submit(4, cheer(ReportReason.ABUSE)),
                ReasonCode.REPORT_TARGET_NOT_FOUND);
        assertThat(repository.cases).isEmpty();
        assertThat(repository.reports).isEmpty();
    }

    @Test
    @DisplayName("TC-6123: 크루 밖 신고자는 NOT_CREW_MEMBER, 본인 콘텐츠는 TARGET_INVALID, 사례 0행(R3·R4)")
    void submit_outsiderAndOwnContent() {
        rejects(() -> service.submit(9, cheer(ReportReason.ABUSE)), ReasonCode.NOT_CREW_MEMBER);
        rejects(() -> service.submit(AUTHOR, cheer(ReportReason.ABUSE)), ReasonCode.TARGET_INVALID);
        assertThat(repository.cases).isEmpty();
    }

    @Test
    @DisplayName("TC-6124: 같은 신고자의 둘째 신고는 ALREADY_REPORTED, 신고 1행·열린 신고자 1(R5)")
    void submit_duplicate() {
        service.submit(4, cheer(ReportReason.SPAM));
        rejects(() -> service.submit(4, cheer(ReportReason.SPAM)), ReasonCode.ALREADY_REPORTED);
        assertThat(repository.reports).hasSize(1);
        assertThat(onlyCase().getOpenReporterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-6125: 첫 신고는 사례를 만들고 RECEIVED, 크루 7, 숨기지 않는다(R6)")
    void submit_first() {
        ReportResult result = service.submit(4, cheer(ReportReason.ABUSE));
        assertThat(result.status()).isEqualTo(ReportStatus.RECEIVED);
        ReportCase c = onlyCase();
        assertThat(c.getCrewId()).isEqualTo(CREW);
        assertThat(c.getOpenReporterCount()).isEqualTo(1);
        assertThat(c.getOpenedAt()).isEqualTo(N);
        assertThat(repository.reports.get(result.reportId()).getReporterId()).isEqualTo(4);
        assertThat(repository.reports.get(result.reportId()).getReportedAt()).isEqualTo(N);
        assertThat(posts.hides).isEmpty();
    }

    @Test
    @DisplayName("TC-6126: 서로 다른 셋째 신고자에서 AUTO_HIDDEN, 채팅 포트 hide 정확히 1회(R8, 경계 2·3명)")
    void submit_thirdReporterAutoHides() {
        ReportCommand chat =
                new ReportCommand(ReportTargetKind.CHAT_MESSAGE, MESSAGE, ReportReason.ABUSE, null);
        assertThat(service.submit(1, chat).status()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(service.submit(2, chat).status()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(chats.hides).isEmpty();
        assertThat(service.submit(4, chat).status()).isEqualTo(ReportStatus.AUTO_HIDDEN);
        assertThat(chats.hides).containsExactly("CHAT_MESSAGE:" + MESSAGE);
        assertThat(posts.hides).isEmpty();
        assertThat(onlyCase().getStatus()).isEqualTo(ReportStatus.AUTO_HIDDEN);
    }

    @Test
    @DisplayName(
            "TC-6127: 기각 뒤 새 신고자는 사례를 다시 열고(0부터 1), 옛 신고자는 ALREADY_REPORTED로 사례를 열지 않는다(R7, D-149)")
    void submit_afterDismissal() {
        for (long r : List.of(1L, 2L, 4L)) {
            service.submit(r, cheer(ReportReason.ABUSE));
        }
        long caseId = onlyCase().getId();
        service.dismiss(OP, caseId);
        assertThat(onlyCase().getStatus()).isEqualTo(ReportStatus.DISMISSED);

        rejects(() -> service.submit(1, cheer(ReportReason.SPAM)), ReasonCode.ALREADY_REPORTED);
        assertThat(onlyCase().getStatus()).isEqualTo(ReportStatus.DISMISSED);

        clock.set(N.plusDays(1));
        ReportResult reopened = service.submit(6, cheer(ReportReason.SPAM));
        assertThat(reopened.status()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(onlyCase().getOpenReporterCount()).isEqualTo(1);
        assertThat(onlyCase().getOpenedAt()).isEqualTo(N.plusDays(1));
        assertThat(repository.openReporterIds(caseId)).containsExactly(6L);
    }

    @Test
    @DisplayName("TC-6130: 없는 사례는 REPORT_NOT_FOUND, 처리된 사례는 REPORT_DECIDED. 감사·알림·포트 호출 0")
    void hideDismiss_rejections() {
        rejects(() -> service.hide(OP, 404), ReasonCode.REPORT_NOT_FOUND);
        rejects(() -> service.dismiss(OP, 404), ReasonCode.REPORT_NOT_FOUND);
        service.submit(4, cheer(ReportReason.ABUSE));
        long caseId = onlyCase().getId();
        service.hide(OP, caseId);
        org.mockito.Mockito.clearInvocations(audit, notifications);
        posts.hides.clear();
        rejects(() -> service.hide(OP, caseId), ReasonCode.REPORT_DECIDED);
        rejects(() -> service.dismiss(OP, caseId), ReasonCode.REPORT_DECIDED);
        verifyNoInteractions(audit, notifications);
        assertThat(posts.hides).isEmpty();
        assertThat(posts.unhides).isEmpty();
    }

    @Test
    @DisplayName(
            "TC-6131 ①: AUTO_HIDDEN 사례 hide는 포트 hide 0회, 열린 신고 resolved, 감사 REPORT_HIDE, 신고자 셋에게"
                    + " REPORT_RESOLVED(크루 홈)")
    void hide_autoHiddenCase() {
        for (long r : List.of(1L, 2L, 4L)) {
            service.submit(r, cheer(ReportReason.ABUSE));
        }
        posts.hides.clear();
        long caseId = onlyCase().getId();
        ReportResolution resolution = service.hide(OP, caseId);
        assertThat(resolution).isEqualTo(new ReportResolution(caseId, ReportStatus.HIDDEN));
        assertThat(posts.hides).isEmpty();
        assertThat(repository.reportsOf(caseId)).allMatch(ContentReport::isResolved);
        assertThat(onlyCase().getResolverId()).isEqualTo(OP);
        assertThat(onlyCase().getResolvedAt()).isEqualTo(N);

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue())
                .isEqualTo(
                        new AuditEntry(
                                OP,
                                ActorRole.OPERATOR,
                                AuditAction.REPORT_HIDE,
                                String.valueOf(caseId),
                                "AUTO_HIDDEN",
                                "HIDDEN"));
        ArgumentCaptor<NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(3)).notify(sent.capture());
        assertThat(sent.getAllValues())
                .extracting(NotificationRequest::recipientId)
                .containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(sent.getAllValues())
                .allSatisfy(
                        n -> {
                            assertThat(n.type()).isEqualTo(NotificationType.REPORT_RESOLVED);
                            assertThat(n.target().screen()).isEqualTo(Screen.CREW_HOME);
                            assertThat(n.target().targetId()).isEqualTo(String.valueOf(CREW));
                        });
    }

    @Test
    @DisplayName("TC-6131 ②: RECEIVED 사례 hide는 포트 hide 1회, 신고자에게 REPORT_RESOLVED 1")
    void hide_receivedCase() {
        service.submit(4, cheer(ReportReason.ABUSE));
        service.hide(OP, onlyCase().getId());
        assertThat(posts.hides).containsExactly("CHEER:" + POST);
        verify(notifications, times(1)).notify(any());
    }

    @Test
    @DisplayName(
            "TC-6132: AUTO_HIDDEN 사례 dismiss는 DISMISSED, 포트 unhide 1회, 감사 REPORT_DISMISS,"
                    + " REPORT_RESOLVED 3")
    void dismiss_autoHiddenCase() {
        for (long r : List.of(1L, 2L, 4L)) {
            service.submit(r, cheer(ReportReason.ABUSE));
        }
        long caseId = onlyCase().getId();
        ReportResolution resolution = service.dismiss(OP, caseId);
        assertThat(resolution.status()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(posts.unhides).containsExactly("CHEER:" + POST);
        assertThat(posts.find(ReportTargetKind.CHEER, POST).orElseThrow().hidden()).isFalse();
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(AuditAction.REPORT_DISMISS);
        assertThat(entry.getValue().targetId()).isEqualTo(String.valueOf(caseId));
        verify(notifications, times(3)).notify(any());
    }

    @Test
    @DisplayName("TC-6131: 탈퇴로 익명화된 신고자에게는 알림을 보내지 않는다")
    void hide_skipsAnonymizedReporters() {
        service.submit(4, cheer(ReportReason.ABUSE));
        service.submit(5, cheer(ReportReason.ABUSE));
        new ReportWithdrawalListener(repository).onMemberWithdrawn(new MemberWithdrawn(4, N));
        service.hide(OP, onlyCase().getId());
        ArgumentCaptor<NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(sent.capture());
        assertThat(sent.getValue().recipientId()).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-6136: 상태 없으면 RECEIVED·AUTO_HIDDEN만 오래된 순, 숨김 미리보기 보임, 지워진 대상은 targetMissing")
    void cases_listing() {
        posts.put(ReportTargetKind.NOTICE, 201, content("공지", false));
        posts.put(ReportTargetKind.BOARD, 202, content("게시글", false));
        posts.put(ReportTargetKind.POLL, 203, content("투표", false));
        posts.put(ReportTargetKind.SCHEDULE, 204, content("일정", false));
        // 201: RECEIVED(N)
        service.submit(4, new ReportCommand(ReportTargetKind.NOTICE, 201, ReportReason.SPAM, null));
        // 202: AUTO_HIDDEN(N+1h)
        clock.set(N.plusHours(1));
        service.submit(
                1, new ReportCommand(ReportTargetKind.BOARD, 202, ReportReason.OTHER, "기타 사유"));
        service.submit(2, new ReportCommand(ReportTargetKind.BOARD, 202, ReportReason.ABUSE, null));
        clock.set(N.plusHours(2));
        service.submit(4, new ReportCommand(ReportTargetKind.BOARD, 202, ReportReason.ABUSE, null));
        // 203: HIDDEN, 204: DISMISSED
        service.submit(4, new ReportCommand(ReportTargetKind.POLL, 203, ReportReason.SPAM, null));
        service.submit(
                4, new ReportCommand(ReportTargetKind.SCHEDULE, 204, ReportReason.SPAM, null));
        long poll =
                repository.findByTargetForUpdate(ReportTargetKind.POLL, 203).orElseThrow().getId();
        long schedule =
                repository
                        .findByTargetForUpdate(ReportTargetKind.SCHEDULE, 204)
                        .orElseThrow()
                        .getId();
        service.hide(OP, poll);
        service.dismiss(OP, schedule);
        // 지워진 대상의 RECEIVED 사례(N+3h)
        clock.set(N.plusHours(3));
        service.submit(4, cheer(ReportReason.PRIVACY));
        posts.remove(ReportTargetKind.CHEER, POST);

        ReportCaseListView open = service.cases(Optional.empty(), PageCursor.all());
        assertThat(open.nextCursor()).isNull();
        assertThat(open.cases())
                .extracting(ReportCaseView::targetId)
                .containsExactly(201L, 202L, POST);
        ReportCaseView board = open.cases().get(1);
        assertThat(board.status()).isEqualTo(ReportStatus.AUTO_HIDDEN);
        assertThat(board.contentPreview()).isEqualTo("게시글");
        assertThat(board.targetMissing()).isFalse();
        assertThat(board.authorId()).isEqualTo(AUTHOR);
        assertThat(board.crewId()).isEqualTo(CREW);
        assertThat(board.reporterCount()).isEqualTo(3);
        assertThat(board.reasons())
                .containsEntry(ReportReason.ABUSE, 2)
                .containsEntry(ReportReason.OTHER, 1)
                .hasSize(2);
        assertThat(board.firstReportedAt()).isEqualTo(N.plusHours(1));
        assertThat(board.lastReportedAt()).isEqualTo(N.plusHours(2));
        ReportCaseView missing = open.cases().get(2);
        assertThat(missing.targetMissing()).isTrue();
        assertThat(missing.contentPreview()).isNull();
        assertThat(missing.authorId()).isNull();

        ReportCaseListView dismissed =
                service.cases(Optional.of(ReportStatus.DISMISSED), PageCursor.all());
        assertThat(dismissed.cases()).extracting(ReportCaseView::caseId).containsExactly(schedule);

        ReportCaseListView first = service.cases(Optional.empty(), new PageCursor(null, 2));
        assertThat(first.cases()).extracting(ReportCaseView::targetId).containsExactly(201L, 202L);
        assertThat(first.nextCursor()).isEqualTo(first.cases().get(1).caseId());
        ReportCaseListView second =
                service.cases(Optional.empty(), new PageCursor(first.nextCursor(), 2));
        assertThat(second.cases()).extracting(ReportCaseView::targetId).containsExactly(POST);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("TC-6137: 탈퇴하면 그 회원의 신고는 신고자가 비고 사례·신고 수는 그대로")
    void withdrawal_anonymizesReporter() {
        service.submit(4, cheer(ReportReason.ABUSE));
        posts.put(ReportTargetKind.BOARD, 202, content("게시글", false));
        service.submit(4, new ReportCommand(ReportTargetKind.BOARD, 202, ReportReason.SPAM, null));
        service.submit(5, cheer(ReportReason.ABUSE));
        new ReportWithdrawalListener(repository).onMemberWithdrawn(new MemberWithdrawn(4, N));
        assertThat(repository.reports.values())
                .extracting(ContentReport::getReporterId)
                .containsExactly(null, null, 5L);
        assertThat(repository.cases).hasSize(2);
        assertThat(
                        repository
                                .findByTargetForUpdate(ReportTargetKind.CHEER, POST)
                                .orElseThrow()
                                .getOpenReporterCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("맡는 포트가 없는 종류는 IllegalStateException(조립 오류)")
    void submit_withoutPort() {
        ReportService postsOnly =
                new ReportService(repository, List.of(posts), notifications, audit, clock);
        assertThatThrownBy(
                        () ->
                                postsOnly.submit(
                                        4,
                                        new ReportCommand(
                                                ReportTargetKind.CHAT_MESSAGE,
                                                MESSAGE,
                                                ReportReason.ABUSE,
                                                null)))
                .isInstanceOf(IllegalStateException.class);
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName(
            "잠금 전에 본 콘텐츠가 보였어도 잠근 사례가 AUTO_HIDDEN·HIDDEN이면 REPORT_TARGET_NOT_FOUND, 신고 행·열린 신고자 수"
                    + " 그대로")
    void submit_rechecksCaseStatusAfterLock() {
        for (long r : List.of(1L, 2L, 4L)) {
            service.submit(r, cheer(ReportReason.ABUSE));
        }
        // 잠금을 기다리는 사이 숨겨진 경우: 포트는 아직 보이는 콘텐츠를 돌려준다
        posts.put(ReportTargetKind.CHEER, POST, content("응원합니다", false));
        rejects(
                () -> service.submit(5, cheer(ReportReason.SPAM)),
                ReasonCode.REPORT_TARGET_NOT_FOUND);
        assertThat(onlyCase().getOpenReporterCount()).isEqualTo(3);
        assertThat(repository.reports).hasSize(3);

        service.hide(OP, onlyCase().getId());
        posts.put(ReportTargetKind.CHEER, POST, content("응원합니다", false));
        rejects(
                () -> service.submit(6, cheer(ReportReason.SPAM)),
                ReasonCode.REPORT_TARGET_NOT_FOUND);
        assertThat(repository.openReporterIds(onlyCase().getId())).isEmpty();
    }

    @Test
    @DisplayName("관리툴 목록의 없는 커서는 IllegalArgumentException(→ 400, 처음부터 다시 주지 않는다)")
    void cases_unknownCursor() {
        assertThatThrownBy(() -> service.cases(Optional.empty(), new PageCursor(987_654L, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 시각을 바꿀 수 있는 시험용 시계. */
    static final class MutableClock extends Clock {
        private java.time.Instant now;
        private final ZoneId zone;

        MutableClock(java.time.Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void set(LocalDateTime at) {
            now = at.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return new MutableClock(now, z);
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }
}

package com.a207.runkk.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditRecordView;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운영자 공지 발송(S15P31A207-275): 기존 알림 경로로 저장과 푸시, 링크 규칙, 탈퇴 회원 건너뛰기, 감사 한 건. */
@DisplayName("운영자 공지 발송")
class NoticeServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = MemberFixtures.NOW;
    private static final String FORM = "https://forms.gle/AbCdEf123";

    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final InMemoryNotificationRepositories.Notifications notifications =
            new InMemoryNotificationRepositories.Notifications();
    private final InMemoryNotificationRepositories.Devices devices =
            new InMemoryNotificationRepositories.Devices();
    private final InMemoryAuditRepository audits = new InMemoryAuditRepository();
    private final List<String> pushedTitles = new ArrayList<>();
    private final List<String> pushedBodies = new ArrayList<>();

    private final PushGateway gateway =
            new PushGateway() {
                @Override
                public PushResult send(List<String> tokens, NotificationType type, String content) {
                    return send(tokens, type, null, content);
                }

                @Override
                public PushResult send(
                        List<String> tokens, NotificationType type, String title, String content) {
                    pushedTitles.add(title);
                    pushedBodies.add(content);
                    return new PushResult(tokens.size(), List.of());
                }

                @Override
                public PushResult sendCollapsing(
                        List<String> tokens,
                        NotificationType type,
                        String content,
                        String collapseKey) {
                    return send(tokens, type, content);
                }
            };

    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final NotificationService notificationService =
            new NotificationService(
                    notifications,
                    devices,
                    new MemberQueryService(members),
                    gateway,
                    new OperationPolicy(new InMemoryOperationSettingRepository()),
                    clock);
    private final AuditService auditService = new AuditService(audits, clock);
    private final NoticeService service = new NoticeService(notificationService, auditService);

    private long member(String nickname) {
        return members.save(MemberFixtures.member(nickname)).getId();
    }

    private static NoticeInput notice(List<Long> ids, String link) {
        return new NoticeInput(ids, "코치님 이벤트 당첨", "구글 폼에 연락처를 남겨 주세요", link);
    }

    @Test
    @DisplayName("고른 회원마다 NOTICE를 알림함에 넣고 공지 제목으로 푸시한다. 링크는 EXTERNAL_LINK 대상 ID다")
    void send_storesAndPushesWithTitleAndLink() {
        long a = member("곰A");
        long b = member("곰B");
        notificationService.registerDevice(a, "token-a");

        NoticeResult result = service.send("KAKAO:op", notice(List.of(a, b, a), FORM));

        assertThat(result).isEqualTo(new NoticeResult(2, 2, List.of()));
        InboxView.Item item = notificationService.inbox(a, Optional.empty()).items().get(0);
        assertThat(item.type()).isEqualTo(NotificationType.NOTICE);
        assertThat(item.title()).isEqualTo("코치님 이벤트 당첨");
        assertThat(item.content()).isEqualTo("구글 폼에 연락처를 남겨 주세요");
        assertThat(item.target()).isEqualTo(new NavigationTarget(Screen.EXTERNAL_LINK, FORM));
        assertThat(notificationService.inbox(b, Optional.empty()).items()).hasSize(1);
        // 트랜잭션 밖이라 바로 보낸다. 토큰이 있는 a만 받는다
        assertThat(pushedTitles).containsExactly("코치님 이벤트 당첨");
        assertThat(pushedBodies).containsExactly("구글 폼에 연락처를 남겨 주세요");
    }

    @Test
    @DisplayName("링크가 없으면 대상 ID 없이 EXTERNAL_LINK(앱은 알림함에 머문다), 다른 종류의 제목은 null")
    void send_withoutLink() {
        long a = member("곰A");
        service.send("KAKAO:op", notice(List.of(a), null));

        InboxView.Item item = notificationService.inbox(a, Optional.empty()).items().get(0);
        assertThat(item.target()).isEqualTo(new NavigationTarget(Screen.EXTERNAL_LINK, null));

        notificationService.notify(
                new NotificationRequest(a, NotificationType.PLAN_MISSED, "x", null));
        InboxView.Item plan =
                notificationService.inbox(a, Optional.empty()).items().stream()
                        .filter(i -> i.type() == NotificationType.PLAN_MISSED)
                        .findFirst()
                        .orElseThrow();
        assertThat(plan.title()).isNull();
    }

    @Test
    @DisplayName("없거나 탈퇴한 회원은 건너뛰고 결과에 적는다")
    void send_skipsWithdrawnAndUnknown() {
        long a = member("곰A");
        long gone = member("곰B");
        members.findById(gone).orElseThrow().withdraw(NOW);

        NoticeResult result = service.send("KAKAO:op", notice(List.of(a, gone, 9_999L), FORM));

        assertThat(result).isEqualTo(new NoticeResult(3, 1, List.of(gone, 9_999L)));
        assertThat(notifications.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("감사는 새 행위 없이 SETTING_CHANGE, 대상 NOTICE:제목, 받는 회원과 결과를 남긴다")
    void send_recordsAudit() {
        long a = member("곰A");
        service.send("KAKAO:op", notice(List.of(a), FORM));

        List<AuditRecordView> records =
                auditService.query(
                        new DateRange(NOW.toLocalDate(), NOW.toLocalDate()), Optional.empty());
        assertThat(records).hasSize(1);
        AuditRecordView r = records.get(0);
        assertThat(r.action()).isEqualTo(AuditAction.SETTING_CHANGE);
        assertThat(r.targetId()).isEqualTo("NOTICE:코치님 이벤트 당첨");
        assertThat(r.actorId()).isEqualTo("KAKAO:op");
        assertThat(r.after()).contains("memberIds=[" + a + "]", FORM, "stored=1");
    }

    @Test
    @DisplayName("링크는 구글 폼 https 주소만 받고 아니면 VALUE_INVALID, 아무것도 보내지 않는다")
    void send_rejectsNonFormLinks() {
        long a = member("곰A");
        for (String bad :
                List.of(
                        "http://forms.gle/AbCdEf123",
                        "https://evil.example/forms/x",
                        "https://forms.gle/",
                        "https://docs.google.com/document/d/1",
                        "https://forms.gle.evil.example/x",
                        "https://user@forms.gle/x",
                        "https://forms.gle:8443/x",
                        "https://forms.gle/a b",
                        "intent://forms.gle/x",
                        "https://forms.gle/" + "a".repeat(NoticeService.LINK_MAX))) {
            assertThatThrownBy(() -> service.send("KAKAO:op", notice(List.of(a), bad)))
                    .as(bad)
                    .isInstanceOfSatisfying(
                            BusinessRuleException.class,
                            e -> assertThat(e.reason()).isEqualTo(ReasonCode.VALUE_INVALID));
        }
        assertThat(notifications.size()).isZero();
        assertThat(audits.size()).isZero();
        assertThat(NoticeService.isFormLink("https://docs.google.com/forms/d/e/1FAIpQLS/viewform"))
                .isTrue();
        assertThat(NoticeService.isFormLink("https://FORMS.GLE/AbC")).isTrue();
    }

    @Test
    @DisplayName("받는 회원이 비었거나 100명을 넘으면 보내지 않는다")
    void send_rejectsRecipientCount() {
        assertThatThrownBy(() -> service.send("KAKAO:op", notice(List.of(), FORM)))
                .isInstanceOf(IllegalArgumentException.class);
        List<Long> many = LongStream.rangeClosed(1, 101).boxed().toList();
        assertThatThrownBy(() -> service.send("KAKAO:op", notice(many, FORM)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(notifications.size()).isZero();
    }
}

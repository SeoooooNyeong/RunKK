package com.a207.runkk.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.Member;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSettings;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.member.SettingChange;
import com.a207.runkk.member.SettingItem;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.NotificationRetention;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** notification 패키지(설계 6.1.10, 상태도 2.4.14, SQ-14). */
@org.junit.jupiter.api.DisplayName("알림 저장·발송·조회")
class NotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = MemberFixtures.NOW;

    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final InMemoryNotificationRepositories.Notifications notifications =
            new InMemoryNotificationRepositories.Notifications();
    private final InMemoryNotificationRepositories.Devices devices =
            new InMemoryNotificationRepositories.Devices();
    private final List<List<String>> pushed = new ArrayList<>();
    private final List<String> invalid = new ArrayList<>();
    private boolean pushFails;

    private final PushGateway gateway =
            (tokens, type, content) -> {
                if (pushFails) {
                    throw new IllegalStateException("FCM down");
                }
                pushed.add(tokens);
                return new PushResult(tokens.size(), invalid);
            };

    private NotificationService serviceAt(LocalDateTime now) {
        return new NotificationService(
                notifications,
                devices,
                new MemberQueryService(members),
                gateway,
                new OperationPolicy(new InMemoryOperationSettingRepository()),
                Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));
    }

    private final NotificationService service = serviceAt(NOW);

    private long member(String nickname) {
        return members.save(MemberFixtures.member(nickname)).getId();
    }

    private static NotificationRequest request(long to, NotificationType type) {
        return new NotificationRequest(to, type, "내용", null);
    }

    @Test
    void notificationType_namesMatchMemberSettingsList_andHaveScreens() {
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name).toList())
                .containsExactlyElementsOf(MemberSettings.NOTIFICATION_TYPES);
        assertThat(NotificationType.PLAN_MISSED.defaultScreen()).isEqualTo(Screen.COACH_PROGRESS);
        assertThat(NotificationType.BODY_SHAPE_CHANGED.defaultScreen()).isEqualTo(Screen.AVATAR);
        assertThat(NotificationType.BATTLE_CORRECTED.defaultScreen())
                .isEqualTo(Screen.BATTLE_RESULT);
    }

    @Test
    void notificationRequest_requiresContent() {
        assertThatThrownBy(() -> new NotificationRequest(1, NotificationType.KICKED, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NavigationTarget(null, "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notification_markReadAndExpiry() {
        Notification n = Notification.of(request(1, NotificationType.KICKED), NOW);
        assertThat(n.isRead()).isFalse();
        n.markRead();
        n.markRead();
        assertThat(n.isRead()).isTrue();
        NotificationRetention retention = new NotificationRetention(30);
        assertThat(n.isExpired(NOW.plusDays(30), retention)).isFalse();
        assertThat(n.isExpired(NOW.plusDays(30).plusSeconds(1), retention)).isTrue();
        assertThat(n.target()).isEqualTo(new NavigationTarget(Screen.CREW_HOME, null));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("L5·L6: 트랜잭션 안에서 부르면 푸시는 커밋 뒤에만 나가고, 롤백(재시도)되면 나가지 않는다")
    void notify_insideTransaction_pushesOnlyAfterCommit() {
        long id = member("곰T");
        service.registerDevice(id, "token-t");
        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        try {
            service.notify(request(id, NotificationType.JOIN_DECIDED));
            assertThat(pushed).isEmpty();
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(
                            org.springframework.transaction.support.TransactionSynchronization
                                    ::afterCommit);
            assertThat(pushed).hasSize(1);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .clearSynchronization();
        }

        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        try {
            service.notify(request(id, NotificationType.JOIN_DECIDED)); // 이 시도는 롤백된다
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .clearSynchronization();
        }
        assertThat(pushed).hasSize(1);
    }

    @Test
    void notify_allowed_savesAndPushes_invalidTokensDeleted() {
        long id = member("곰A");
        service.registerDevice(id, "token-1");
        service.registerDevice(id, "token-2");
        invalid.add("token-2");

        service.notify(
                new NotificationRequest(
                        id,
                        NotificationType.JOIN_DECIDED,
                        "가입이 승인되었어요",
                        new NavigationTarget(Screen.CREW_HOME, "77")));

        assertThat(notifications.size()).isEqualTo(1);
        assertThat(pushed).containsExactly(List.of("token-1", "token-2"));
        assertThat(devices.tokensOf(id)).containsExactly("token-1");
    }

    @Test
    void notify_muted_savesOnly_withdrawn_dropsAll() {
        long muted = member("곰A");
        Member m = members.findById(muted).orElseThrow();
        m.changeSetting(new SettingChange(SettingItem.ALERT, "false", "KICKED"));
        service.registerDevice(muted, "t");
        service.notify(request(muted, NotificationType.KICKED));
        assertThat(notifications.size()).isEqualTo(1);
        assertThat(pushed).isEmpty();

        long gone = member("곰B");
        members.findById(gone).orElseThrow().withdraw(NOW);
        service.notify(request(gone, NotificationType.KICKED));
        assertThat(notifications.size()).isEqualTo(1);
    }

    @Test
    void notify_pushFailure_isLoggedNotThrown_andNoDevicesMeansNoPush() {
        long id = member("곰A");
        service.notify(request(id, NotificationType.KICKED));
        assertThat(pushed).isEmpty();

        service.registerDevice(id, "t");
        pushFails = true;
        service.notify(request(id, NotificationType.KICKED));
        assertThat(notifications.size()).isEqualTo(2);
    }

    @Test
    void inbox_newestFirst_withRange_andOpenMarksRead() {
        long id = member("곰A");
        long other = member("곰B");
        serviceAt(NOW.minusDays(3)).notify(request(id, NotificationType.PLAN_MISSED));
        service.notify(request(id, NotificationType.KICKED));
        service.notify(request(other, NotificationType.KICKED));

        InboxView inbox = service.inbox(id, Optional.empty());
        assertThat(inbox.items())
                .extracting(InboxView.Item::type)
                .containsExactly(NotificationType.KICKED, NotificationType.PLAN_MISSED);
        assertThat(
                        service.inbox(
                                        id,
                                        Optional.of(
                                                new DateRange(
                                                        LocalDate.of(2026, 10, 20),
                                                        LocalDate.of(2026, 10, 20))))
                                .items())
                .hasSize(1);

        long first = inbox.items().get(0).id();
        assertThat(service.open(id, first)).isEqualTo(new NavigationTarget(Screen.CREW_HOME, null));
        assertThat(service.inbox(id, Optional.empty()).items().get(0).read()).isTrue();
        assertThatThrownBy(() -> service.open(other, first))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.open(id, 999)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void registerDevice_reassignsSameTokenToNewOwner() {
        long a = member("곰A");
        long b = member("곰B");
        service.registerDevice(a, "shared");
        service.registerDevice(a, "shared");
        assertThat(devices.size()).isEqualTo(1);
        service.registerDevice(b, "shared");
        assertThat(devices.tokensOf(a)).isEmpty();
        assertThat(devices.tokensOf(b)).containsExactly("shared");
        assertThatThrownBy(() -> service.registerDevice(a, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerDevice(a, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void purgeExpired_usesRetentionDays() {
        long id = member("곰A");
        serviceAt(NOW.minusDays(31)).notify(request(id, NotificationType.KICKED));
        serviceAt(NOW.minusDays(29)).notify(request(id, NotificationType.KICKED));
        assertThat(service.purgeExpired(NOW)).isEqualTo(1);
        assertThat(notifications.size()).isEqualTo(1);
    }

    @Test
    void withdrawalListener_deletesNotificationsAndDevices() {
        long id = member("곰A");
        long other = member("곰B");
        service.registerDevice(id, "t1");
        service.registerDevice(other, "t2");
        service.notify(request(id, NotificationType.KICKED));
        service.notify(request(other, NotificationType.KICKED));

        new NotificationWithdrawalListener(notifications, devices)
                .onMemberWithdrawn(new MemberWithdrawn(id, NOW));

        assertThat(notifications.findByRecipient(id, Optional.empty())).isEmpty();
        assertThat(devices.tokensOf(id)).isEmpty();
        assertThat(devices.tokensOf(other)).containsExactly("t2");
        assertThat(notifications.size()).isEqualTo(1);
    }
}

package com.a207.runkk.notification;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.PushDecision;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 저장·발송·조회 유스케이스를 조정한다(설계 6.1.10, SQ-14). 수신 설정과 관계없이 알림함에 저장하고(W-03), 그 종류를 켠 회원에게만 푸시를
 * 보낸다(FR-175). 푸시 실패는 운영 로그만 남기고 다시 보내지 않는다. Member 엔티티를 받지 않는다(A62).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final MemberQueryService memberQueryService;
    private final PushGateway pushGateway;
    private final OperationPolicy policy;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            PushDeviceRepository pushDeviceRepository,
            MemberQueryService memberQueryService,
            PushGateway pushGateway,
            OperationPolicy policy,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.memberQueryService = memberQueryService;
        this.pushGateway = pushGateway;
        this.policy = policy;
        this.clock = clock;
    }

    /** 탈퇴 회원이면 버림 → 알림함 저장 → ALLOWED면 커밋 뒤 기기 토큰으로 푸시(MUTED면 저장만). 무효 토큰은 지운다. */
    @Transactional
    public void notify(NotificationRequest req) {
        PushDecision decision =
                memberQueryService.pushDecision(req.recipientId(), req.type().name());
        if (decision == PushDecision.WITHDRAWN) {
            log.debug("notification_dropped type={} reason=withdrawn", req.type());
            return;
        }
        notificationRepository.save(Notification.of(req, LocalDateTime.now(clock)));
        if (decision != PushDecision.ALLOWED) {
            return;
        }
        List<String> tokens = pushDeviceRepository.tokensOf(req.recipientId());
        if (tokens.isEmpty()) {
            return;
        }
        afterCommit(
                () -> push(req, tokens, () -> pushGateway.send(tokens, req.type(), req.content())));
    }

    /**
     * 묶음 알림(CREW_CHAT, FR-218, D-102). 같은 수신자·종류·대상(target.targetId)에 읽지 않은 알림이 있으면 새 행을 만들지 않고 그
     * 행의 내용·일시를 바꾸고, 없으면 notify처럼 저장한다. 수신 설정은 notify와 같고(탈퇴 버림, MUTED 저장만), 푸시는 묶음 키 "종류_대상"(예
     * crew_chat_7)으로 보내 기기 알림도 한 건으로 묶는다. 대상 ID가 없으면 notify와 같다. 같은 대상의 동시 호출도 읽지 않은 행은 하나다
     * (NotificationRepository.saveCollapsing: V23 부분 유일 색인 + 충돌하면 고쳐 쓰기, 리뷰 M4).
     */
    @Transactional
    public void notifyCollapsing(NotificationRequest req) {
        if (req.target() == null || req.target().targetId() == null) {
            notify(req); // 묶을 대상이 없으면 묶지 않는다(묶음 키 "종류_null" 방지)
            return;
        }
        PushDecision decision =
                memberQueryService.pushDecision(req.recipientId(), req.type().name());
        if (decision == PushDecision.WITHDRAWN) {
            log.debug("notification_dropped type={} reason=withdrawn", req.type());
            return;
        }
        String targetId = req.target().targetId();
        notificationRepository.saveCollapsing(
                new NotificationRequest(
                        req.recipientId(),
                        req.type(),
                        Notification.fit(req.content()),
                        req.target()),
                LocalDateTime.now(clock));
        if (decision != PushDecision.ALLOWED) {
            return;
        }
        List<String> tokens = pushDeviceRepository.tokensOf(req.recipientId());
        if (tokens.isEmpty()) {
            return;
        }
        String collapseKey = req.type().name().toLowerCase(Locale.ROOT) + "_" + targetId;
        afterCommit(
                () ->
                        push(
                                req,
                                tokens,
                                () ->
                                        pushGateway.sendCollapsing(
                                                tokens, req.type(), req.content(), collapseKey)));
    }

    /**
     * 외부 호출(FCM)을 커밋 뒤로 미룬다. 주의: 이미 커밋된 트랜잭션의 AFTER_COMMIT 단계에서 새 트랜잭션 없이 notify를 부르면 여기서 등록한 동기화가
     * 불리지 않는다. AFTER_COMMIT 수신자는 REQUIRES_NEW 트랜잭션(BattleTransactions·CrewTransactions 등) 안에서
     * 부른다(java-reviewer M5). 트랜잭션 밖이면 바로 한다. 부른 쪽 트랜잭션이 낙관적 잠금 충돌로 재시도·롤백되면 그 시도의 푸시는 나가지 않아 같은 알림이
     * 두 번 가지 않는다(코드 리뷰 2차 L5·L6).
     */
    private static void afterCommit(Runnable effect) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            effect.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        effect.run();
                    }
                });
    }

    /** 푸시 발송. 무효 토큰은 지운다(새 트랜잭션, PushDeviceRepository 구현). 실패는 로그만. */
    private void push(NotificationRequest req, List<String> tokens, Supplier<PushResult> sending) {
        try {
            PushResult result = sending.get();
            if (!result.invalidTokens().isEmpty()) {
                pushDeviceRepository.deleteTokens(result.invalidTokens());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "push_failed type={} devices={} error={}",
                    req.type(),
                    tokens.size(),
                    e.getClass().getSimpleName());
        }
    }

    /** 알림함, 최신순. 기간이 있으면 그 안의 것만(FR-178). */
    @Transactional(readOnly = true)
    public InboxView inbox(long memberId, Optional<DateRange> range) {
        return new InboxView(
                notificationRepository.findByRecipient(memberId, range).stream()
                        .map(
                                n ->
                                        new InboxView.Item(
                                                n.id(),
                                                n.type(),
                                                n.content(),
                                                n.occurredAt(),
                                                n.isRead(),
                                                n.target()))
                        .toList());
    }

    /** 본인 알림만 열어 읽음으로 바꾸고 이동 대상을 돌려준다. 남의 알림이면 PermissionDeniedException. */
    @Transactional
    public NavigationTarget open(long memberId, long notificationId) {
        Notification notification =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(
                                () -> new BusinessRuleException(ReasonCode.NOTIFICATION_NOT_FOUND));
        if (notification.recipientId() != memberId) {
            throw new PermissionDeniedException("남의 알림");
        }
        notification.markRead();
        notificationRepository.save(notification);
        return notification.target();
    }

    /** PUT /me/push-token. 같은 토큰이 다른 회원으로 오면 주인을 바꾸고, 같은 회원이면 그대로 둔다(D-43). */
    @Transactional
    public void registerDevice(long memberId, String token) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<PushDevice> existing =
                token == null ? Optional.empty() : pushDeviceRepository.findByToken(token);
        if (existing.isEmpty()) {
            pushDeviceRepository.save(PushDevice.register(memberId, token, now));
            return;
        }
        PushDevice device = existing.get();
        if (device.memberId() != memberId) {
            device.reassign(memberId, now);
            pushDeviceRepository.save(device);
        }
    }

    /** 보관 기한(policy.notificationRetention, 기본 30일)이 지난 알림 삭제. batch.notification이 3시에 부른다. */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        int days = policy.notificationRetention().days();
        int deleted = notificationRepository.deleteOlderThan(now.minusDays(days));
        log.info("notification_purged count={}", deleted);
        return deleted;
    }
}

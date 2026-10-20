package com.a207.runkk.notification;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴한 회원의 알림(D17)과 기기 토큰을 지운다(설계 6.1.10, FR-021, D-43). 탈퇴 커밋 뒤 새 트랜잭션에서 돈다(커밋 뒤 수신자는 원래 트랜잭션에 합류하면
 * 쓰기가 커밋되지 않는다).
 */
@Component
public class NotificationWithdrawalListener {

    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;

    public NotificationWithdrawalListener(
            NotificationRepository notificationRepository,
            PushDeviceRepository pushDeviceRepository) {
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        notificationRepository.deleteByRecipient(e.memberId());
        pushDeviceRepository.deleteByMember(e.memberId());
    }
}

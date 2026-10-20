package com.a207.runkk.policy;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.DomainEventPublisher;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 운영 기준값 변경 유스케이스를 조정한다(설계 6.1.9). policy는 battle을 부르지 않고 사건만 발행한다(D-34). */
@Service
public class OperationSettingService {

    private final OperationSettingRepository repository;
    private final OperationPolicy policy;
    private final AuditService auditService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public OperationSettingService(
            OperationSettingRepository repository,
            OperationPolicy policy,
            AuditService auditService,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * OperationSetting.change(값 검사, 바꾸기 전후 검사) → 이력 저장 → 감사(SETTING_CHANGE, 전후 값, 행위자
     * actorId·OPERATOR) → 커밋 뒤 OperationPolicy.invalidate(key) → OperationSettingChanged(key, now)
     * 발행(FR-174). actorId는 web.admin이 확인한 운영자 계정이다(D-50).
     */
    @Transactional
    public SettingChangeResult change(String actorId, SettingKey key, String value) {
        LocalDateTime now = LocalDateTime.now(clock);
        OperationSetting setting =
                repository.find(key).orElseGet(() -> OperationSetting.initial(key, now));
        OperationSettingHistory history = setting.change(value, actorId, now);
        repository.save(setting);
        repository.saveHistory(history);
        auditService.record(
                new AuditEntry(
                        actorId,
                        ActorRole.OPERATOR,
                        AuditAction.SETTING_CHANGE,
                        key.name(),
                        history.before(),
                        history.after()));
        invalidateAfterCommit(key);
        eventPublisher.publish(new OperationSettingChanged(key, now));
        return new SettingChangeResult(key, history.before(), history.after());
    }

    /** 사건 수신자(커밋 뒤)가 새 값을 읽도록 사건 발행보다 먼저 등록한다. */
    private void invalidateAfterCommit(SettingKey key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            policy.invalidate(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        policy.invalidate(key);
                    }
                });
    }
}

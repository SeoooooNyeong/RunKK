package com.a207.runkk.battle;

import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingChanged;
import com.a207.runkk.policy.SettingKey;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가상 기록 생성·교체(설계 6.1.6, srs.md 6.3 7.5, FR-104, Q-86). 서비스 시작 때 한 번 만들고, 티어 경계 또는 가상 기록 규칙이 바뀌면 다시
 * 만든다(D-45). 회원 닉네임과 겹치는 가상 닉네임을 만들지 않는다(H-11').
 */
@Service
public class VirtualRecordService {

    /** 가상 기록 생성의 입력이 되는 운영 기준(D-45, design-review.md H-14). */
    static final Set<SettingKey> INPUT_KEYS =
            Set.of(SettingKey.TIER_BOUNDARY, SettingKey.VIRTUAL_RECORD_RULE);

    private static final Logger log = LoggerFactory.getLogger(VirtualRecordService.class);

    private final VirtualRecordRepository virtualRecordRepository;
    private final VirtualRecordFactory virtualRecordFactory;
    private final MemberQueryService memberQueryService;
    private final OperationPolicy operationPolicy;

    public VirtualRecordService(
            VirtualRecordRepository virtualRecordRepository,
            VirtualRecordFactory virtualRecordFactory,
            MemberQueryService memberQueryService,
            OperationPolicy operationPolicy) {
        this.virtualRecordRepository = virtualRecordRepository;
        this.virtualRecordFactory = virtualRecordFactory;
        this.memberQueryService = memberQueryService;
        this.operationPolicy = operationPolicy;
    }

    /** 시작 때. 운영 기준이 정한 개수(기본 100개)가 아니면 다시 만든다. */
    @Transactional
    public void ensureGenerated() {
        long expected = expectedCount();
        long actual = virtualRecordRepository.count();
        if (actual != expected) {
            log.info(
                    "virtual_records_regenerate reason=count actual={} expected={}",
                    actual,
                    expected);
            regenerate();
        }
    }

    /** VirtualRecordFactory.generate(티어 경계, 가상 기록 규칙, 회원 닉네임 존재) → 기존 전부 교체(한 트랜잭션). 만든 수. */
    @Transactional
    public int regenerate() {
        List<VirtualRecord> records =
                virtualRecordFactory.generate(
                        operationPolicy.tierBoundary(),
                        operationPolicy.virtualRecordRule(),
                        memberQueryService::isNicknameTaken);
        virtualRecordRepository.replaceAll(records);
        return records.size();
    }

    /** policy가 발행한 사건을 커밋 뒤 받는다. TIER_BOUNDARY·VIRTUAL_RECORD_RULE이면 다시 만든다(D-34, D-45). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOperationSettingChanged(OperationSettingChanged e) {
        if (INPUT_KEYS.contains(e.key())) {
            int count = regenerate();
            log.info("virtual_records_regenerate reason={} count={}", e.key(), count);
        }
    }

    private long expectedCount() {
        return VirtualRecordFactory.expectedCount(operationPolicy.virtualRecordRule());
    }
}

package com.a207.runkk.battle;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유명인 기록 등록·삭제(설계 6.1.6, srs.md 6.3 7.1, FR-170~FR-172). actorId는 web.admin의
 * OperatorCheckInterceptor가 확인한 운영자 계정이고 이 서비스는 operator를 import하지 않는다(D-34, D-50). 진행 중·끝난 대결의
 * OpponentRef는 삭제 뒤에도 남는다(FR-172).
 */
@Service
public class CelebrityRecordService {

    private final CelebrityRecordRepository celebrityRecordRepository;
    private final AuditService auditService;
    private final Clock clock;

    public CelebrityRecordService(
            CelebrityRecordRepository celebrityRecordRepository,
            AuditService auditService,
            Clock clock) {
        this.celebrityRecordRepository = celebrityRecordRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** CelebrityRecord.register → 저장 → 감사(유명인기록등록, OPERATOR). */
    @Transactional
    public CelebrityResult register(String actorId, CelebrityInput in) {
        CelebrityRecord saved =
                celebrityRecordRepository.save(
                        CelebrityRecord.register(in, LocalDateTime.now(clock)));
        auditService.record(
                new AuditEntry(
                        actorId,
                        ActorRole.OPERATOR,
                        AuditAction.CELEBRITY_REGISTER,
                        String.valueOf(saved.getId()),
                        null,
                        // 등록 감사에 실명 동의 여부를 함께 남긴다(v0.14 D-240)
                        saved.isRealName()
                                ? saved.getAnonymousName() + " (실명 동의)"
                                : saved.getAnonymousName()));
        return new CelebrityResult(saved.getId(), CelebrityResult.Outcome.REGISTERED);
    }

    /** 원래 값까지 지운다 → 감사(유명인기록삭제, 사유). 없으면 RECORD_NOT_FOUND("기록 없음"). */
    @Transactional
    public CelebrityResult delete(String actorId, long celebrityId, String reason) {
        CelebrityRecord record =
                celebrityRecordRepository
                        .findById(celebrityId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.RECORD_NOT_FOUND));
        celebrityRecordRepository.delete(celebrityId);
        auditService.record(
                new AuditEntry(
                        actorId,
                        ActorRole.OPERATOR,
                        AuditAction.CELEBRITY_DELETE,
                        String.valueOf(celebrityId),
                        record.getAnonymousName(),
                        reason));
        return new CelebrityResult(celebrityId, CelebrityResult.Outcome.DELETED);
    }
}

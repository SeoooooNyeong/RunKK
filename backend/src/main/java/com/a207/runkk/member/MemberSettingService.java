package com.a207.runkk.member;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.StandardDistance;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 설정·동의 변경 유스케이스를 조정한다(설계 6.1.2, srs.md 6.3 1.4, SQ-13). */
@Service
public class MemberSettingService {

    private final MemberRepository memberRepository;
    private final AuditService auditService;
    private final DomainEventPublisher eventPublisher;
    private final LegalDocumentCatalog legalDocumentCatalog;
    private final Clock clock;

    public MemberSettingService(
            MemberRepository memberRepository,
            AuditService auditService,
            DomainEventPublisher eventPublisher,
            LegalDocumentCatalog legalDocumentCatalog,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.legalDocumentCatalog = legalDocumentCatalog;
        this.clock = clock;
    }

    /** 동의가 아닌 설정만. Member.changeSetting → 저장. */
    @Transactional
    public SettingResult changeSetting(long memberId, SettingChange change) {
        Member member = MemberLoader.load(memberRepository, memberId);
        SettingOutcome outcome = member.changeSetting(change);
        memberRepository.save(member);
        return new SettingResult(change.item(), outcome, member.getSettings());
    }

    /**
     * Member.giveConsent → 저장 → 감사(동의변경). 사용 시점 동의(위치·심박·얼굴 변환·LLM)도 이 연산이다(FR-013~FR-016, FR-019).
     */
    @Transactional
    public ConsentResult giveConsent(long memberId, ConsentItem item) {
        return giveConsent(memberId, item, 1);
    }

    @Transactional
    public ConsentResult giveConsent(long memberId, ConsentItem item, int noticeVersion) {
        Member member = MemberLoader.load(memberRepository, memberId);
        ConsentChange change = member.giveConsent(item, LocalDateTime.now(clock), noticeVersion);
        memberRepository.save(member);
        audit(memberId, change);
        return new ConsentResult(item, change.before(), change.after());
    }

    /** 위치·심박·LLM 동의를 철회하면 ConsentWithdrawn을 발행한다. 러닝 자료 파기와 코치 중계 취소는 각 수신자가 맡는다. */
    @Transactional
    public ConsentResult withdrawConsent(long memberId, ConsentItem item) {
        Member member = MemberLoader.load(memberRepository, memberId);
        ConsentChange change = member.withdrawConsent(item, LocalDateTime.now(clock));
        memberRepository.save(member);
        audit(memberId, change);
        if (change.before()
                && (item == ConsentItem.LOCATION
                        || item == ConsentItem.HEART_RATE
                        || item == ConsentItem.LLM_TRANSFER)) {
            eventPublisher.publish(new ConsentWithdrawn(memberId, item, change.at()));
        }
        return new ConsentResult(item, change.before(), change.after());
    }

    /** battle.MatchingService가 매칭 요청 때 부른다(FR-080). 다른 패키지가 회원 설정을 고치는 유일한 경로다(A44). */
    @Transactional
    public void rememberBattleDistance(long memberId, StandardDistance d) {
        Member member = MemberLoader.load(memberRepository, memberId);
        member.rememberBattleDistance(d);
        memberRepository.save(member);
    }

    /** 배틀런 설정(기본 대결 거리 포함, FR-109). battle.MatchingService가 매칭 상태와 함께 앱에 준다. */
    @Transactional(readOnly = true)
    public BattleSettings battleSettingsOf(long memberId) {
        return MemberLoader.load(memberRepository, memberId).getSettings().battleSettings();
    }

    /** 문서 주소(FR-018). */
    public LegalDocument legalDocument(DocumentType type) {
        return legalDocumentCatalog.find(type);
    }

    /** GET /me/settings. Member.getSettings의 불변 사본(D-53, D-57). */
    @Transactional(readOnly = true)
    public MemberSettingsView settingsOf(long memberId) {
        return MemberLoader.load(memberRepository, memberId).getSettings();
    }

    private void audit(long memberId, ConsentChange change) {
        String id = String.valueOf(memberId);
        auditService.record(
                new AuditEntry(
                        id,
                        ActorRole.RUNNER,
                        AuditAction.CONSENT_CHANGE,
                        id,
                        change.item() + "=" + change.before(),
                        change.item() + "=" + change.after()));
    }
}

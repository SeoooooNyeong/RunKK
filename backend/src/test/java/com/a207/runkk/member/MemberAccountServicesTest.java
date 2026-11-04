package com.a207.runkk.member;

import static com.a207.runkk.member.MemberFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.audit.InMemoryAuditRepository;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ProfileService·MemberSettingService·WithdrawalService·TierLevelService·MemberQueryService(인메모리
 * 저장소·고정 시계).
 */
@org.junit.jupiter.api.DisplayName("프로필·설정·탈퇴·티어·회원 조회 서비스")
class MemberAccountServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private final InMemoryMemberRepository repository = new InMemoryMemberRepository();
    private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
    private final AuditService auditService = new AuditService(auditRepository, clock);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisher publisher = published::add;
    private final LegalDocumentCatalog catalog =
            new LegalDocumentCatalog("https://t", "https://p", "https://l", "https://h");
    private final MemberSettingService settingService =
            new MemberSettingService(repository, auditService, publisher, catalog, clock);
    private final MemberQueryService query = new MemberQueryService(repository);

    private long saved(String nickname) {
        return repository.save(MemberFixtures.member(nickname)).getId();
    }

    @Test
    void editProfile_rejectsTakenNickname_allowsOwnNickname() {
        long a = saved("곰A");
        saved("곰B");
        ProfileService service =
                new ProfileService(repository, List.of(n -> n.equals("가상곰")), clock);

        assertThatThrownBy(() -> service.editProfile(a, new ProfileEdit("곰B", null, null, null)))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.NICKNAME_TAKEN);
        assertThatThrownBy(() -> service.editProfile(a, new ProfileEdit("가상곰", null, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        ProfileEditResult same = service.editProfile(a, new ProfileEdit("곰A", 2000, null, "소개"));
        assertThat(same.nickname()).isEqualTo("곰A");
        assertThat(same.birthYear()).isEqualTo(2000);
        assertThat(same.photoUrl()).isNull();
        ProfileEditResult renamed = service.editProfile(a, new ProfileEdit("곰C", null, null, null));
        assertThat(renamed.nickname()).isEqualTo("곰C");
    }

    @Test
    void changeSetting_and_settingsOf() {
        long id = saved("달리는곰");
        SettingResult result =
                settingService.changeSetting(
                        id, new SettingChange(SettingItem.COURSE_MAP_SHARED, "true", null));
        assertThat(result.outcome().applied()).isTrue();
        assertThat(settingService.settingsOf(id).courseMapShared()).isTrue();

        SettingResult watch =
                settingService.changeSetting(
                        id, new SettingChange(SettingItem.WATCH_LINK, "true", null));
        assertThat(watch.outcome().requiredConsent()).isEqualTo(ConsentItem.HEART_RATE);
        assertThat(watch.settings().watchLinked()).isFalse();
    }

    @Test
    void withdrawConsent_location_auditsAndPublishesOnce() {
        long id = saved("달리는곰");
        ConsentResult result = settingService.withdrawConsent(id, ConsentItem.LOCATION);
        assertThat(result.before()).isTrue();
        assertThat(result.after()).isFalse();
        assertThat(published).containsExactly(new ConsentWithdrawn(id, ConsentItem.LOCATION, NOW));
        assertThat(auditRepository.all())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.action()).isEqualTo(AuditAction.CONSENT_CHANGE);
                            assertThat(a.before()).isEqualTo("LOCATION=true");
                            assertThat(a.after()).isEqualTo("LOCATION=false");
                        });

        settingService.withdrawConsent(id, ConsentItem.LOCATION);
        assertThat(published).hasSize(1);
        assertThat(auditRepository.size()).isEqualTo(2);
    }

    @Test
    void withdrawConsent_marketing_doesNotPublish_giveConsentAudits() {
        long id = saved("달리는곰");
        settingService.giveConsent(id, ConsentItem.MARKETING);
        settingService.withdrawConsent(id, ConsentItem.MARKETING);
        assertThat(published).isEmpty();
        assertThat(auditRepository.size()).isEqualTo(2);

        settingService.giveConsent(id, ConsentItem.HEART_RATE);
        settingService.withdrawConsent(id, ConsentItem.HEART_RATE);
        assertThat(published)
                .containsExactly(new ConsentWithdrawn(id, ConsentItem.HEART_RATE, NOW));
    }

    @Test
    void rememberBattleDistance_and_legalDocument() {
        long id = saved("달리는곰");
        settingService.rememberBattleDistance(id, StandardDistance.KM3);
        assertThat(settingService.settingsOf(id).battleSettings().defaultDistance())
                .isEqualTo(StandardDistance.KM3);
        assertThat(settingService.legalDocument(DocumentType.PRIVACY_POLICY))
                .isEqualTo(new LegalDocument(DocumentType.PRIVACY_POLICY, "https://p"));
    }

    /** 코드 리뷰 3차 B-L7: 설정이 빠진 법정 고지 주소는 null 응답이 아니라 기동 실패다. */
    @Test
    void legalCatalog_missingUrl_failsAtStartup() {
        assertThatThrownBy(
                        () -> new LegalDocumentCatalog("https://t", null, "https://l", "https://h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runkk.legal.privacy-policy-url");
        assertThatThrownBy(
                        () -> new LegalDocumentCatalog("https://t", "https://p", " ", "https://h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runkk.legal.location-terms-url");
    }

    @Test
    void withdraw_guardRejects_orWithdrawsAuditsAndPublishes() {
        long id = saved("달리는곰");
        WithdrawalService rejecting =
                new WithdrawalService(
                        repository,
                        new InMemoryPlacementProgressRepository(),
                        List.of(
                                m -> Optional.empty(),
                                m -> Optional.of(ReasonCode.LEADER_MUST_TRANSFER)),
                        List.of(),
                        auditService,
                        publisher,
                        clock);
        assertThatThrownBy(() -> rejecting.withdraw(id))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.LEADER_MUST_TRANSFER);
        assertThat(repository.findById(id).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);

        WithdrawalService service =
                new WithdrawalService(
                        repository,
                        new InMemoryPlacementProgressRepository(),
                        List.of(),
                        List.of(),
                        auditService,
                        publisher,
                        clock);
        assertThat(service.withdraw(id).withdrawn()).isTrue();
        assertThat(published).containsExactly(new MemberWithdrawn(id, NOW));
        assertThat(auditRepository.all())
                .singleElement()
                .satisfies(a -> assertThat(a.action()).isEqualTo(AuditAction.WITHDRAWAL));
        assertThatThrownBy(() -> service.withdraw(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void purgeWithdrawn_onlyAfterSevenDays() {
        long early = repository.save(MemberFixtures.member("곰A", NOW.minusDays(30))).getId();
        long late = repository.save(MemberFixtures.member("곰B", NOW.minusDays(30))).getId();
        saved("곰C");
        WithdrawalService service =
                new WithdrawalService(
                        repository,
                        new InMemoryPlacementProgressRepository(),
                        List.of(),
                        List.of(),
                        auditService,
                        publisher,
                        clock);
        repository.findById(early).orElseThrow().withdraw(NOW.minusDays(8));
        repository.findById(late).orElseThrow().withdraw(NOW.minusDays(6));

        assertThat(service.purgeWithdrawn(NOW)).isEqualTo(1);
        assertThat(repository.findById(early).orElseThrow().getSettings().recordVisibility())
                .isEqualTo(RecordVisibility.PRIVATE);
        assertThat(published).containsExactly(new MemberPurged(early, NOW));
    }

    @Test
    void purgeWithdrawn_publishesPurgedOnlyInRetryWindow() {
        long fresh = repository.save(MemberFixtures.member("곰D", NOW.minusDays(60))).getId();
        long old = repository.save(MemberFixtures.member("곰E", NOW.minusDays(60))).getId();
        WithdrawalService service =
                new WithdrawalService(
                        repository,
                        new InMemoryPlacementProgressRepository(),
                        List.of(),
                        List.of(),
                        auditService,
                        publisher,
                        clock);
        repository.findById(fresh).orElseThrow().withdraw(NOW.minusDays(10));
        repository.findById(old).orElseThrow().withdraw(NOW.minusDays(11));

        assertThat(service.purgeWithdrawn(NOW)).isEqualTo(2);

        assertThat(published).containsExactly(new MemberPurged(fresh, NOW));
        assertThat(repository.findById(old).orElseThrow().getSettings().recordVisibility())
                .isEqualTo(RecordVisibility.PRIVATE);
    }

    @Test
    void tierLevelService_usesPolicyBoundary_andIgnoresRepeatedOrdinal() {
        long id = saved("달리는곰");
        TierLevelService service =
                new TierLevelService(
                        repository,
                        new InMemoryPlacementProgressRepository(),
                        new OperationPolicy(new InMemoryOperationSettingRepository()));
        repository.findById(id).orElseThrow().confirmPlacement(Tier.BRONZE);
        for (int i = 1; i <= 10; i++) {
            service.recordValidRun(id, new RunScoreInput(41, Distance.ofKm(5), i));
        }
        assertThat(repository.findById(id).orElseThrow().getTier()).isEqualTo(Tier.PLATINUM);
        TierChange again = service.recordValidRun(id, new RunScoreInput(20, Distance.ofKm(5), 10));
        assertThat(again.tierChanged()).isFalse();
    }

    @Test
    void memberQueryService_projections() {
        long a = saved("곰A");
        long b = saved("곰B");
        long c = saved("곰C");
        Member memberC = repository.findById(c).orElseThrow();
        memberC.withdraw(NOW);
        Member memberB = repository.findById(b).orElseThrow();
        memberB.changeSetting(new SettingChange(SettingItem.PROFILE_VISIBILITY, "PUBLIC", null));
        memberB.changeSetting(new SettingChange(SettingItem.ALERT, "false", "KICKED"));
        memberB.giveConsent(ConsentItem.LLM_TRANSFER, NOW);

        assertThat(query.summaries(List.of(a, c)).get(c).displayName())
                .isEqualTo(DisplayName.WITHDRAWN);
        assertThat(query.summaries(List.of())).isEmpty();
        assertThat(query.preferenceOf(b).llmAgreed()).isTrue();
        assertThat(query.ageOf(a, 2026)).hasValue(31);
        assertThat(query.isNicknameTaken("곰A")).isTrue();
        assertThat(query.hasConsent(a, ConsentItem.LOCATION)).isTrue();
        assertThat(query.hasConsent(999, ConsentItem.LOCATION)).isFalse();
        assertThat(query.pushDecision(a, "KICKED")).isEqualTo(PushDecision.ALLOWED);
        assertThat(query.pushDecision(b, "KICKED")).isEqualTo(PushDecision.MUTED);
        assertThat(query.pushDecision(c, "KICKED")).isEqualTo(PushDecision.WITHDRAWN);
        assertThat(query.pushDecision(999, "KICKED")).isEqualTo(PushDecision.WITHDRAWN);
        assertThat(query.rankedMemberIds()).containsExactlyInAnyOrder(a, b);
        assertThat(query.disclosureOf(a, List.of(a, b, c)))
                .containsEntry(a, DisclosureLevel.SELF)
                .containsEntry(b, DisclosureLevel.PUBLIC)
                .containsEntry(c, DisclosureLevel.WITHDRAWN);
        // v0.14 D-241: 다른 러너는 늘 PUBLIC 수준이고 나이대·성별만 항목별로 감춘다
        assertThat(query.disclosureOf(b, List.of(a))).containsEntry(a, DisclosureLevel.PUBLIC);
        assertThat(query.disclosureOf(a, List.of())).isEmpty();
        assertThat(query.ghostEligibleOwners(Set.of(Tier.UNRANKED), a).keySet()).containsExactly(b);
        assertThat(query.ghostEligibleOwners(Set.of(), a)).isEmpty();
        // 표시 성별(D-327·D-336): 성별 공개(b)는 그 값, 비공개(a, 기본값)·성별 없음(탈퇴한 c)·없는 회원은 회원 ID 고정 대체값
        assertThat(query.shownGendersOf(List.of(a, b, c, 999L)))
                .containsOnly(
                        Map.entry(a, ShownGender.fallbackOf(a)),
                        Map.entry(b, Gender.FEMALE),
                        Map.entry(c, ShownGender.fallbackOf(c)),
                        Map.entry(999L, ShownGender.fallbackOf(999L)));
        assertThat(query.shownGendersOf(List.of())).isEmpty();
    }
}

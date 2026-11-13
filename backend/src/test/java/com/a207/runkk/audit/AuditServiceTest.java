package com.a207.runkk.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.DateRange;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("감사 기록 저장·조회·정리")
class AuditServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 15, 0);

    private final InMemoryAuditRepository repository = new InMemoryAuditRepository();

    private AuditService serviceAt(LocalDateTime now) {
        return new AuditService(repository, Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));
    }

    private static AuditEntry entry(AuditAction action) {
        return new AuditEntry("op@kakao", ActorRole.OPERATOR, action, "TIER_BOUNDARY", "a", "b");
    }

    @Test
    void auditActionAndActorRole_matchDataDictionary() {
        // 자료사전 11개 + 운영자 로그인 성공·실패(L25) + 랭킹 다시 계산 + v0.11 5개(운영진 지정·해제, 채팅 강제 삭제, 신고 숨김·기각)
        assertThat(AuditAction.values()).hasSize(23);
        assertThat(ActorRole.values())
                .containsExactly(
                        ActorRole.RUNNER,
                        ActorRole.CREW_LEADER,
                        ActorRole.CREW_MANAGER,
                        ActorRole.OPERATOR,
                        ActorRole.SYSTEM);
    }

    @Test
    void auditEntry_requiresActorRoleActionAndTarget() {
        assertThatThrownBy(
                        () ->
                                new AuditEntry(
                                        null, ActorRole.SYSTEM, AuditAction.KICK, "1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEntry("1", null, AuditAction.KICK, "1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEntry("1", ActorRole.RUNNER, null, "1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AuditEntry(
                                        "1", ActorRole.RUNNER, AuditAction.KICK, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditRecord_isImmutable_noPublicMutators() {
        List<String> publicMethods =
                Arrays.stream(AuditRecord.class.getDeclaredMethods())
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .map(Method::getName)
                        .toList();
        assertThat(publicMethods).containsExactly("of");
    }

    @Test
    void auditRecord_of_copiesEntryWithTime() {
        AuditRecord record = AuditRecord.of(entry(AuditAction.SETTING_CHANGE), NOW);
        assertThat(record.view())
                .isEqualTo(
                        new AuditRecordView(
                                NOW,
                                "op@kakao",
                                ActorRole.OPERATOR,
                                AuditAction.SETTING_CHANGE,
                                "TIER_BOUNDARY",
                                "a",
                                "b"));
    }

    @Test
    void record_appendsWithClockTime() {
        serviceAt(NOW).record(entry(AuditAction.SETTING_CHANGE));

        assertThat(repository.all()).singleElement().extracting(AuditRecordView::at).isEqualTo(NOW);
    }

    @Test
    void query_filtersRangeAndActionNewestFirst() {
        serviceAt(NOW.minusDays(3)).record(entry(AuditAction.KICK));
        serviceAt(NOW.minusDays(1)).record(entry(AuditAction.SETTING_CHANGE));
        serviceAt(NOW).record(entry(AuditAction.SETTING_CHANGE));
        AuditService service = serviceAt(NOW);

        DateRange lastTwoDays =
                new DateRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20));
        assertThat(service.query(lastTwoDays, Optional.empty()))
                .extracting(AuditRecordView::at)
                .containsExactly(NOW, NOW.minusDays(1));
        DateRange week = new DateRange(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 20));
        assertThat(service.query(week, Optional.of(AuditAction.KICK)))
                .extracting(AuditRecordView::action)
                .containsExactly(AuditAction.KICK);
    }

    @Test
    void purgeExpired_deletesRecordsOlderThanSixMonths() {
        serviceAt(NOW.minusMonths(6).minusSeconds(1)).record(entry(AuditAction.KICK));
        serviceAt(NOW.minusMonths(6)).record(entry(AuditAction.KICK));
        serviceAt(NOW).record(entry(AuditAction.KICK));

        int purged = serviceAt(NOW).purgeExpired(NOW);

        assertThat(purged).isEqualTo(1);
        assertThat(repository.size()).isEqualTo(2);
    }
}

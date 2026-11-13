package com.a207.runkk.aiprivacy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.member.MemberQueryService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiPurgeServiceTest {
    private final MemoryRequests requests = new MemoryRequests();
    private final AiDataEraser eraser = mock(AiDataEraser.class);
    private final AuditService audit = mock(AuditService.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

    private AiPurgeService service(LocalDateTime time) {
        return new AiPurgeService(
                requests,
                eraser,
                audit,
                Clock.fixed(
                        time.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")),
                members,
                NoTransactions.manager());
    }

    @Test
    void failureRetriesNextDayAndStopsOnSuccess() {
        var service = service(now);
        service.register(1, now);
        doThrow(new AiUnavailableException("STORAGE_UNAVAILABLE"))
                .doNothing()
                .when(eraser)
                .erase(1);
        service.attempt(1, false);
        assertThat(requests.rows.get(1L).status()).isEqualTo(PurgeStatus.PENDING);
        service.retryDue();
        verify(eraser).erase(1);
        service(now.plusDays(1)).retryDue();
        service(now.plusDays(2)).retryDue();
        verify(eraser, times(2)).erase(1);
        assertThat(requests.rows.get(1L).status()).isEqualTo(PurgeStatus.DONE);
    }

    @Test
    void overdueIsAuditedOnceWhileRetryContinues() {
        service(now).register(1, now);
        doThrow(new AiUnavailableException("STORAGE_UNAVAILABLE")).when(eraser).erase(1);
        service(now.plusDays(7)).retryDue();
        service(now.plusDays(8)).retryDue();
        verify(audit, times(1)).record(any());
        verify(eraser, times(2)).erase(1);
        assertThat(requests.rows.get(1L).status()).isEqualTo(PurgeStatus.ALERTED);
    }

    @Test
    void retentionEventRepeatsSuccessfulRequestAndRestoresMissingHistory() {
        service(now).register(1, now);
        service(now).attempt(1, false);
        service(now.plusDays(7)).purgeResidual(1, now.plusDays(7));
        service(now.plusDays(7)).purgeResidual(2, now.plusDays(7));
        verify(eraser, times(2)).erase(1);
        verify(eraser).erase(2);
        assertThat(requests.rows.get(2L).status()).isEqualTo(PurgeStatus.DONE);
    }

    @Test
    void dailyScanBackfillsOldWithdrawalsOutsideMemberPurgeEventWindow() {
        when(members.withdrawalsBefore(any())).thenReturn(Map.of(5L, now.minusDays(20)));
        service(now).retryDue();
        verify(eraser).erase(5);
        assertThat(requests.rows.get(5L).status()).isEqualTo(PurgeStatus.DONE);
    }

    @Test
    void oneMemberDatabaseFailureDoesNotBlockOthers() {
        var service = service(now);
        service.register(1, now);
        service.register(2, now);
        requests.brokenId = 1;
        service.retryDue();
        verify(eraser, never()).erase(1);
        verify(eraser).erase(2);
    }

    static class MemoryRequests implements AiPurgeRequestRepository {
        final Map<Long, AiPurgeRequest> rows = new LinkedHashMap<>();
        long brokenId = -1;

        public Optional<AiPurgeRequest> findForUpdate(long id) {
            if (id == brokenId) throw new IllegalStateException("DB unavailable");
            return Optional.ofNullable(rows.get(id));
        }

        public void save(AiPurgeRequest request) {
            rows.put(request.memberId(), request);
        }

        public List<Long> dueMembers(LocalDateTime time) {
            return new ArrayList<>(rows.keySet());
        }
    }
}

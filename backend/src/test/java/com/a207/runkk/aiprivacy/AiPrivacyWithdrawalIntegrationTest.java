package com.a207.runkk.aiprivacy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.member.*;
import com.a207.runkk.web.WebTestSupport;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

class AiPrivacyWithdrawalIntegrationTest extends WebTestSupport {
    @MockitoBean AiDataEraser eraser;
    @MockitoBean Clock clock;
    @Autowired AiPurgeService service;
    @Autowired AiPurgeRequestRepository requests;
    @Autowired WithdrawalService withdrawals;
    @Autowired MemberQueryService memberQueries;
    @Autowired MemberRepository members;
    @Autowired DomainEventPublisher events;
    @Autowired TransactionTemplate tx;
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);

    @BeforeEach
    void time() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        at(now);
    }

    private void at(LocalDateTime time) {
        when(clock.instant()).thenReturn(time.atZone(ZoneId.of("Asia/Seoul")).toInstant());
    }

    private PurgeStatus purgeStatus(long id) {
        return tx.execute(s -> requests.findForUpdate(id).orElseThrow().status());
    }

    @Test
    void aiFailureDoesNotBlockWithdrawalAndRetriesNextDay() throws Exception {
        Session user = signUp();
        doThrow(new AiUnavailableException("STORAGE_UNAVAILABLE"))
                .doNothing()
                .when(eraser)
                .erase(user.memberId());
        mvc.perform(as(user, delete("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawn").value(true));
        verify(eraser).erase(user.memberId());
        assertThat(memberQueries.isActive(user.memberId())).isFalse();
        assertThat(purgeStatus(user.memberId())).isEqualTo(PurgeStatus.PENDING);
        at(now.plusDays(1));
        service.retryDue();
        verify(eraser, times(2)).erase(user.memberId());
        assertThat(purgeStatus(user.memberId())).isEqualTo(PurgeStatus.DONE);
    }

    @Test
    void sevenDayMemberPurgeCallsDeleteAgainAfterInitialSuccess() throws Exception {
        Session user = signUp();
        mvc.perform(as(user, delete("/api/v1/me"))).andExpect(status().isOk());
        verify(eraser).erase(user.memberId());
        at(now.plusDays(7).plusMinutes(1));
        withdrawals.purgeWithdrawn(LocalDateTime.now(clock));
        verify(eraser, times(2)).erase(user.memberId());
        assertThat(purgeStatus(user.memberId())).isEqualTo(PurgeStatus.DONE);
    }

    @Test
    void rolledBackEventLeavesNoRequestAndNeverDeletes() throws Exception {
        Session user = signUp();
        tx.executeWithoutResult(
                s -> {
                    events.publish(new MemberWithdrawn(user.memberId(), now));
                    s.setRollbackOnly();
                });
        verify(eraser, never()).erase(user.memberId());
        boolean present = tx.execute(s -> requests.findForUpdate(user.memberId()).isPresent());
        assertThat(present).isFalse();
        assertThat(memberQueries.isActive(user.memberId())).isTrue();
    }

    @Test
    void missedRetentionEventIsRecoveredByDailyScan() throws Exception {
        Session user = signUp();
        mvc.perform(as(user, delete("/api/v1/me"))).andExpect(status().isOk());
        at(now.plusDays(12));
        service.retryDue();
        verify(eraser, times(2)).erase(user.memberId());
        service.retryDue();
        verify(eraser, times(2)).erase(user.memberId());
    }

    @Test
    void dailyScanFindsOldMembersWithoutAnyQueueRow() throws Exception {
        at(now.minusDays(20));
        Session user = signUp();
        tx.executeWithoutResult(
                s -> {
                    Member member = members.findById(user.memberId()).orElseThrow();
                    member.withdraw(now.minusDays(20));
                    members.save(member);
                });
        at(now);
        service.retryDue();
        verify(eraser).erase(user.memberId());
        assertThat(purgeStatus(user.memberId())).isEqualTo(PurgeStatus.DONE);
    }

    @Test
    void retentionBatchAlsoRepairsMembersWithdrawnBeforeThisFeature() throws Exception {
        at(now.minusDays(8));
        Session user = signUp();
        tx.executeWithoutResult(
                s -> {
                    Member member = members.findById(user.memberId()).orElseThrow();
                    member.withdraw(now.minusDays(8));
                    members.save(member);
                });
        at(now);
        withdrawals.purgeWithdrawn(now);
        verify(eraser).erase(user.memberId());
        assertThat(purgeStatus(user.memberId())).isEqualTo(PurgeStatus.DONE);
    }
}

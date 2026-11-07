package com.a207.runkk.web.runbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.runbody.RunBodyGateway;
import com.a207.runkk.runbody.RunBodyJob;
import com.a207.runkk.runbody.RunBodyJobRepository;
import com.a207.runkk.runbody.RunBodyMonth;
import com.a207.runkk.runbody.RunBodySession;
import com.a207.runkk.runbody.RunBodyStatus;
import com.a207.runkk.runbody.RunBodyUnavailableException;
import com.a207.runkk.web.WebTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 런바디 웹 계층(05-19 2.5, 앱 계약 runbody contract.md v1.0). 인증 필터·오류 응답까지 실제로 돌리고 ai 게이트웨이만 가짜다(test 프로필
 * MockMvc, 다른 web 시험과 같은 방식).
 */
@DisplayName("런바디 API")
class RunBodyControllerTest extends WebTestSupport {

    private static final String RESULT =
            "{\"sessionId\":\"%s\",\"userId\":\"%d\",\"schemaVersion\":2,"
                    + "\"axes\":{\"form\":{\"score\":null,\"level\":\"none\"}},\"metrics\":{}}";

    @MockitoBean RunBodyGateway gateway;
    @Autowired RunBodyJobRepository jobs;
    @Autowired OperationSettingService settings;
    @Autowired TransactionTemplate tx;

    private void open(boolean on) {
        settings.change("test", SettingKey.RUNBODY_ENABLED, Boolean.toString(on));
    }

    @AfterEach
    void close() {
        open(false);
    }

    private String job(long memberId, Consumer<RunBodyJob> state) {
        String recordId = UUID.randomUUID().toString();
        tx.executeWithoutResult(
                s -> {
                    RunBodyJob job = RunBodyJob.pending(recordId, memberId, LocalDateTime.now());
                    state.accept(job);
                    jobs.save(job);
                });
        return recordId;
    }

    @Test
    void closedFeature_returns403FeatureClosed_onBothEndpoints() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_CLOSED"));
        mvc.perform(as(s, get("/api/v1/runbody/months/2026-10")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_CLOSED"));
    }

    @Test
    void session_withoutJobOrOfAnotherMember_is404() throws Exception {
        open(true);
        Session s = signUp();
        Session other = signUp();
        String othersRecord = job(other.memberId(), j -> {});
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUNBODY_NOT_FOUND"));
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", othersRecord)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUNBODY_NOT_FOUND"));
    }

    @Test
    void session_beforeDone_hasStatusAndNullResult() throws Exception {
        open(true);
        Session s = signUp();
        String pending = job(s.memberId(), j -> {});
        String analyzing = job(s.memberId(), j -> j.submitted(LocalDateTime.now()));
        String failed = job(s.memberId(), RunBodyJob::rejected);

        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", pending)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.recordId").value(pending))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", analyzing)))
                .andExpect(jsonPath("$.status").value("ANALYZING"));
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", failed)))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void session_done_passesAiResultAsJsonObject() throws Exception {
        open(true);
        Session s = signUp();
        String done = job(s.memberId(), RunBodyJob::done);
        String result = RESULT.formatted(done, s.memberId());
        when(gateway.sessionOf(done))
                .thenReturn(RunBodySession.done(Long.toString(s.memberId()), result));

        String body =
                mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", done)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("DONE"))
                        .andExpect(jsonPath("$.result.sessionId").value(done))
                        .andExpect(jsonPath("$.result.schemaVersion").value(2))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode node = body(body);
        assertThat(node.get("result")).isEqualTo(json.readTree(result));
        assertThat(node.get("result").get("axes").get("form").get("score").isNull()).isTrue();
    }

    @Test
    void session_doneButAiPendingOrDown_mapsToAnalyzingOr503() throws Exception {
        open(true);
        Session s = signUp();
        String done = job(s.memberId(), RunBodyJob::done);
        when(gateway.sessionOf(done)).thenReturn(RunBodySession.notReady(RunBodyStatus.ANALYZING));
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", done)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANALYZING"));

        when(gateway.sessionOf(done))
                .thenThrow(new RunBodyUnavailableException("TIMEOUT_OR_IO", false));
        mvc.perform(as(s, get("/api/v1/runbody/sessions/{id}", done)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RUNBODY_UNAVAILABLE"));
    }

    @Test
    void month_passesAiBody_andValidatesFormat() throws Exception {
        open(true);
        Session s = signUp();
        String month =
                "{\"userId\":\"%d\",\"yearMonth\":\"2026-10\",\"sessionCount\":0,\"load\":{\"available\":false,\"ratio\":null}}"
                        .formatted(s.memberId());
        when(gateway.monthOf(s.memberId(), YearMonth.of(2026, 10)))
                .thenReturn(new RunBodyMonth(Long.toString(s.memberId()), month));

        String body =
                mvc.perform(as(s, get("/api/v1/runbody/months/2026-10")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.yearMonth").value("2026-10"))
                        .andExpect(jsonPath("$.sessionCount").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body(body)).isEqualTo(json.readTree(month));

        for (String bad : new String[] {"2026-13", "1999-01", "2026-1", "202610"}) {
            mvc.perform(as(s, get("/api/v1/runbody/months/{ym}", bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_YEAR_MONTH"));
        }
    }

    @Test
    void month_aiDown_is503() throws Exception {
        open(true);
        Session s = signUp();
        when(gateway.monthOf(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RunBodyUnavailableException("STATUS_503", false));
        mvc.perform(as(s, get("/api/v1/runbody/months/2026-11")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RUNBODY_UNAVAILABLE"));
    }

    @Test
    void withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/runbody/months/2026-10")).andExpect(status().isUnauthorized());
    }
}

package com.a207.runkk.web.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.aicoach.AiCoachService;
import com.a207.runkk.aicoach.CoachChatGateway;
import com.a207.runkk.aicoach.CoachQuestion;
import com.a207.runkk.aicoach.CoachRejected;
import com.a207.runkk.aicoach.CoachStore;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.web.WebTestSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiCoachControllerTest extends WebTestSupport {
    private static final String LIST = "/api/v1/coach/conversations";
    private static final String QUESTIONS = "/api/v1/coach/questions";
    private static final String CONSENT = "/api/v1/me/consents/LLM_TRANSFER";

    @Autowired OperationSettingService settings;
    @Autowired CoachStore store;
    @Autowired AiCoachService coach;
    @MockitoBean CoachChatGateway gateway;
    @LocalServerPort int port;

    @AfterEach
    void closeCoach() {
        setCoach(false);
        settings.change("test", SettingKey.COACH_DAILY_LIMIT, "20");
    }

    @Test
    void closedCoachReturnsFeatureClosed() throws Exception {
        mvc.perform(as(signUp(), get(LIST)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_CLOSED"));
        verifyNoInteractions(gateway);
    }

    @Test
    void legacyConsentCannotUnlockCoachButExplicitV2Can() throws Exception {
        setCoach(true);
        Session session = signUp();
        mvc.perform(as(session, put(CONSENT))).andExpect(status().isOk());
        ask(session, null, "질문")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(as(session, put(CONSENT).param("version", "3")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(session, put(CONSENT).param("version", "2"))).andExpect(status().isOk());
        mvc.perform(as(session, get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(as(session, delete(CONSENT))).andExpect(status().isOk());
        mvc.perform(as(session, get(LIST)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        verifyNoInteractions(gateway);
    }

    @Test
    void acceptsOnlyAuthenticatedOwnerAndCountsDone() throws Exception {
        Session owner = eligible();
        Session other = eligible();
        settings.change("test", SettingKey.COACH_DAILY_LIMIT, "1");
        String id = UUID.randomUUID().toString();
        String question = "😀".repeat(500);
        when(gateway.open(owner.memberId(), new CoachQuestion(null, question)))
                .thenReturn(
                        stream(
                                out -> {
                                    out.started(id);
                                    out.delta("회복 러닝");
                                    out.done();
                                }));
        mvc.perform(
                        post(QUESTIONS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"질문\"}"))
                .andExpect(status().isUnauthorized());
        ask(owner, null, question + "😀")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_TOO_LONG"));
        MvcResult result =
                ask(owner, null, question).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        assertThat(result.getResponse().getContentAsString())
                .containsSubsequence("event:started", id, "event:delta", "회복 러닝", "event:done");
        mvc.perform(as(owner, get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value(id))
                .andExpect(jsonPath("$[0].firstQuestion").value("😀".repeat(40)));
        mvc.perform(as(other, get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        ask(other, id, "질문")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONVERSATION_EXPIRED"));
        ask(owner, id, "질문")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COACH_QUOTA_EXCEEDED"));
        verify(gateway).open(owner.memberId(), new CoachQuestion(null, question));
    }

    @Test
    void authenticatesActualHttpStreamAndRejectsOversizedBodyBeforeOpeningAi() throws Exception {
        Session owner = eligible();
        ask(owner, null, " ".repeat(8192) + "질문")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_TOO_LONG"));
        verifyNoInteractions(gateway);

        String id = UUID.randomUUID().toString();
        when(gateway.open(owner.memberId(), new CoachQuestion(null, "질문")))
                .thenReturn(
                        stream(
                                out -> {
                                    out.started(id);
                                    out.delta("실제 HTTP 답변");
                                    out.done();
                                }));
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + QUESTIONS))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", owner.bearer())
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        write(new CoachQuestionPayload(null, "질문"))))
                        .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .containsSubsequence("event:started", id, "실제 HTTP 답변", "event:done");
        }
    }

    @Test
    void revokingConsentClosesActiveAiStreamAndStopsDelivery() throws Exception {
        Session owner = eligible();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        String id = UUID.randomUUID().toString();
        when(gateway.open(owner.memberId(), new CoachQuestion(null, "질문")))
                .thenReturn(
                        new CoachChatGateway.Stream() {
                            @Override
                            public void read(CoachChatGateway.Listener out) throws IOException {
                                out.started(id);
                                reading.countDown();
                                try {
                                    if (!closed.await(5, TimeUnit.SECONDS)) {
                                        throw new IOException("test stream still open");
                                    }
                                } catch (InterruptedException failure) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(failure);
                                }
                                out.delta("철회 후 답변");
                                out.done();
                            }

                            @Override
                            public void close() {
                                closed.countDown();
                            }
                        });
        MvcResult result = ask(owner, null, "질문").andExpect(request().asyncStarted()).andReturn();
        assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
        mvc.perform(as(owner, delete(CONSENT))).andExpect(status().isOk());
        assertThat(closed.getCount()).isZero();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString())
                .contains("event:failed")
                .doesNotContain("event:delta", "event:done");
    }

    @Test
    void failedStreamReleasesLeaseAndKeepsStartedConversationForRetry() throws Exception {
        Session owner = eligible();
        settings.change("test", SettingKey.COACH_DAILY_LIMIT, "1");
        String id = UUID.randomUUID().toString();
        when(gateway.open(owner.memberId(), new CoachQuestion(null, "처음")))
                .thenReturn(
                        stream(
                                out -> {
                                    out.started(id);
                                    throw new IOException("unexpected EOF");
                                }));
        MvcResult failed = ask(owner, null, "처음").andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(failed)).andExpect(status().isOk());
        assertThat(failed.getResponse().getContentAsString())
                .containsSubsequence("event:started", "event:failed", "\"reason\":\"RETRY\"")
                .doesNotContain("event:done");
        when(gateway.open(owner.memberId(), new CoachQuestion(id, "다시")))
                .thenReturn(
                        stream(
                                out -> {
                                    out.started(id);
                                    out.delta("답변");
                                    out.done();
                                }));
        MvcResult retry = ask(owner, id, "다시").andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(retry)).andExpect(status().isOk());
        assertThat(retry.getResponse().getContentAsString()).contains("event:done");
        mvc.perform(as(owner, get(LIST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstQuestion").value("처음"));
    }

    @Test
    void parallelQuestionCannotOpenSecondUpstreamWhileLeaseHeld() throws Exception {
        Session owner = eligible();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        String id = UUID.randomUUID().toString();
        when(gateway.open(eq(owner.memberId()), any()))
                .thenReturn(
                        stream(
                                out -> {
                                    reading.countDown();
                                    try {
                                        if (!resume.await(5, TimeUnit.SECONDS)) {
                                            throw new IOException("test did not resume");
                                        }
                                    } catch (InterruptedException failure) {
                                        Thread.currentThread().interrupt();
                                        throw new IOException(failure);
                                    }
                                    out.started(id);
                                    out.done();
                                }));
        MvcResult first = ask(owner, null, "처음").andExpect(request().asyncStarted()).andReturn();
        try {
            assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
            MvcResult second =
                    ask(owner, null, "동시").andExpect(request().asyncStarted()).andReturn();
            mvc.perform(asyncDispatch(second)).andExpect(status().isOk());
            assertThat(second.getResponse().getContentAsString())
                    .contains("event:failed", "\"reason\":\"RETRY\"");
            verify(gateway).open(eq(owner.memberId()), any());
        } finally {
            resume.countDown();
        }
        mvc.perform(asyncDispatch(first)).andExpect(status().isOk());
    }

    @Test
    void expiredLeaseIsFencedAndSuccessfulCountResetsNextDay() throws Exception {
        Session owner = signUp();
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 23, 0);
        CoachStore.Lease old = store.claim(owner.memberId(), 1, now);
        CoachStore.Lease newer = store.claim(owner.memberId(), 1, now.plusMinutes(2));
        store.release(old);
        assertThat(store.answered(old, now.plusMinutes(2))).isFalse();
        assertThat(store.answered(newer, now.plusMinutes(2))).isTrue();
        assertThatThrownBy(() -> store.claim(owner.memberId(), 1, now.plusMinutes(3)))
                .isInstanceOf(CoachRejected.class)
                .hasMessage("COACH_QUOTA_EXCEEDED");
        CoachStore.Lease nextDay = store.claim(owner.memberId(), 1, now.plusHours(1));
        store.release(nextDay);
    }

    @Test
    void expiredAndWithdrawnConversationIndexesAreRemoved() throws Exception {
        Session owner = eligible();
        LocalDateTime old = LocalDateTime.now().minusDays(91);
        CoachStore.Lease lease = store.claim(owner.memberId(), 20, old);
        String id = UUID.randomUUID().toString();
        assertThat(store.started(lease, id, "과거", false, old)).isTrue();
        store.release(lease);
        ask(owner, id, "질문").andExpect(status().isGone());
        coach.purgeExpired();
        assertThat(store.hasConversation(owner.memberId(), id, old.minusDays(1))).isFalse();

        LocalDateTime now = LocalDateTime.now();
        CoachStore.Lease current = store.claim(owner.memberId(), 20, now);
        assertThat(store.started(current, id, "새 대화", false, now)).isTrue();
        mvc.perform(as(owner, delete("/api/v1/me"))).andExpect(status().isOk());
        assertThat(store.answered(current, now.plusSeconds(1))).isFalse();
        assertThat(store.hasConversation(owner.memberId(), id, old)).isFalse();
        verifyNoInteractions(gateway);
    }

    private Session eligible() throws Exception {
        setCoach(true);
        Session session = signUp();
        mvc.perform(as(session, put(CONSENT).param("version", "2"))).andExpect(status().isOk());
        return session;
    }

    private ResultActions ask(Session session, String id, String text) throws Exception {
        return mvc.perform(
                as(
                        session,
                        post(QUESTIONS)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(new CoachQuestionPayload(id, text)))));
    }

    private void setCoach(boolean enabled) {
        settings.change("test", SettingKey.COACH_ENABLED, Boolean.toString(enabled));
    }

    private static CoachChatGateway.Stream stream(Events events) {
        return new CoachChatGateway.Stream() {
            @Override
            public void read(CoachChatGateway.Listener listener) throws IOException {
                events.read(listener);
            }

            @Override
            public void close() {}
        };
    }

    private interface Events {
        void read(CoachChatGateway.Listener listener) throws IOException;
    }

    private record CoachQuestionPayload(String conversationId, String text) {}
}

package com.a207.runkk.web.coach;

import com.a207.runkk.aicoach.AiCoachService;
import com.a207.runkk.aicoach.CoachChatGateway;
import com.a207.runkk.aicoach.CoachQuestion;
import com.a207.runkk.aicoach.CoachRejected;
import com.a207.runkk.aicoach.CoachStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/coach")
public class AiCoachController {
    private static final Logger log = LoggerFactory.getLogger(AiCoachController.class);
    private static final long TIMEOUT_MILLIS = 60_000L;
    private static final int BODY_MAX_BYTES = 8 * 1024;

    private final AiCoachService coach;
    private final ObjectReader questions;
    private final Semaphore capacity = new Semaphore(50);
    private final SimpleAsyncTaskExecutor streams = new SimpleAsyncTaskExecutor("coach-sse-");

    public AiCoachController(AiCoachService coach, ObjectMapper json) {
        this.coach = coach;
        this.questions =
                json.readerFor(QuestionRequest.class)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        streams.setVirtualThreads(true);
    }

    @GetMapping("/conversations")
    ResponseEntity<List<CoachStore.Conversation>> conversations(
            @RequestAttribute("memberId") long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(coach.conversationsOf(memberId));
    }

    @Operation(
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    QuestionRequest.class))))
    @PostMapping(
            value = "/questions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> ask(
            @RequestAttribute("memberId") long memberId, HttpServletRequest request) {
        CoachQuestion question = questionFrom(request);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        try {
            start(emitter, prepare(memberId, question));
        } catch (CoachRejected rejection) {
            if (rejection.reason() != CoachRejected.Reason.RETRY) {
                throw rejection;
            }
            complete(emitter, true);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8))
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private CoachQuestion questionFrom(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(BODY_MAX_BYTES + 1);
            if (bytes.length > BODY_MAX_BYTES) {
                throw new CoachRejected(CoachRejected.Reason.QUESTION_TOO_LONG);
            }
            QuestionRequest payload = questions.readValue(bytes);
            if (payload == null) {
                throw new IllegalArgumentException("빈 코치 요청");
            }
            return new CoachQuestion(payload.conversationId(), payload.text());
        } catch (IOException invalid) {
            throw new IllegalArgumentException("잘못된 코치 요청");
        }
    }

    private AiCoachService.Session prepare(long memberId, CoachQuestion question) {
        if (!capacity.tryAcquire()) {
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        }
        try {
            return coach.prepare(memberId, question);
        } catch (RuntimeException failure) {
            capacity.release();
            throw failure;
        }
    }

    private void start(SseEmitter emitter, AiCoachService.Session session) {
        AppEvents events = new AppEvents(emitter, session);
        AtomicBoolean released = new AtomicBoolean();
        Runnable close =
                () -> {
                    if (released.compareAndSet(false, true)) {
                        try {
                            session.close();
                        } finally {
                            capacity.release();
                        }
                    }
                };
        FutureTask<Void> task =
                new FutureTask<>(
                        () -> {
                            boolean failed = false;
                            try {
                                session.relay(events);
                            } catch (IOException failure) {
                                failed = true;
                            } catch (RuntimeException failure) {
                                log.warn(
                                        "coach_relay_failed type={}",
                                        failure.getClass().getSimpleName());
                                failed = true;
                            } finally {
                                close.run();
                            }
                            events.finish(failed);
                            return null;
                        });
        Runnable cancel =
                () -> {
                    task.cancel(true);
                    close.run();
                };
        emitter.onTimeout(
                () -> {
                    cancel.run();
                    events.finish(true);
                });
        emitter.onError(
                failure -> {
                    cancel.run();
                    events.abandon();
                });
        emitter.onCompletion(cancel);
        try {
            streams.execute(task);
        } catch (RejectedExecutionException stopped) {
            cancel.run();
            events.finish(true);
        }
    }

    private static void complete(SseEmitter emitter, boolean failed) {
        try {
            if (failed) {
                send(emitter, "failed", Map.of("reason", "RETRY"));
            }
            emitter.complete();
        } catch (IOException | IllegalStateException disconnected) {
            emitter.completeWithError(disconnected);
        }
    }

    private static void send(SseEmitter emitter, String name, Map<String, String> data)
            throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
    }

    @ExceptionHandler(CoachRejected.class)
    ResponseEntity<Map<String, String>> rejected(CoachRejected failure) {
        HttpStatus status =
                switch (failure.reason()) {
                    case FEATURE_CLOSED, CONSENT_REQUIRED -> HttpStatus.FORBIDDEN;
                    case CONVERSATION_EXPIRED -> HttpStatus.GONE;
                    case COACH_QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
                    case QUESTION_TOO_LONG -> HttpStatus.BAD_REQUEST;
                    case RETRY -> HttpStatus.SERVICE_UNAVAILABLE;
                };
        String message =
                switch (failure.reason()) {
                    case FEATURE_CLOSED -> "아직 열리지 않은 기능입니다";
                    case CONSENT_REQUIRED -> "AI 전송 안내에 동의해 주세요";
                    case CONVERSATION_EXPIRED -> "대화가 만료됐어요";
                    case COACH_QUOTA_EXCEEDED -> "코치 질문 한도에 도달했어요";
                    case QUESTION_TOO_LONG -> "질문은 500자까지 입력할 수 있어요";
                    case RETRY -> "다시 물어봐 주세요";
                };
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", failure.reason().name(), "message", message));
    }

    @PreDestroy
    void closeStreams() {
        streams.close();
    }

    record QuestionRequest(String conversationId, @NotBlank String text) {}

    private static final class AppEvents implements CoachChatGateway.Listener {
        private final SseEmitter emitter;
        private final AiCoachService.Session session;
        private boolean terminal;
        private boolean finished;

        private AppEvents(SseEmitter emitter, AiCoachService.Session session) {
            this.emitter = emitter;
            this.session = session;
        }

        @Override
        public synchronized void started(String conversationId) throws IOException {
            requireOpen();
            send(emitter, "started", Map.of("conversationId", conversationId));
        }

        @Override
        public synchronized void delta(String text) throws IOException {
            requireOpen();
            send(emitter, "delta", Map.of("text", text));
        }

        @Override
        public synchronized void done() throws IOException {
            requireOpen();
            session.acknowledgeAnswer();
            terminal = true;
            send(emitter, "done", Map.of());
        }

        private synchronized void finish(boolean failed) {
            if (!finished) {
                finished = true;
                boolean sendFailure = failed && !terminal;
                terminal = true;
                complete(emitter, sendFailure);
            }
        }

        private synchronized void abandon() {
            terminal = true;
            finished = true;
        }

        private void requireOpen() throws IOException {
            if (terminal) {
                throw new IOException("coach response already completed");
            }
        }
    }
}
